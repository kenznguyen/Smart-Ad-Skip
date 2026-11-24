package com.kenznguyen.smartadskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.drawable.GradientDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityNodeInfo
import com.kenznguyen.smartadskip.ai.AiRuntimeDashboardStatus
import com.kenznguyen.smartadskip.ai.AdAction
import com.kenznguyen.smartadskip.ai.AdFrameContext
import com.kenznguyen.smartadskip.ai.AdUiElement
import com.kenznguyen.smartadskip.ai.AutonomousTargetSafetyPolicy
import com.kenznguyen.smartadskip.ai.AutonomousDecision
import com.kenznguyen.smartadskip.ai.AutonomousAdDecisionEngine
import com.kenznguyen.smartadskip.ai.LlamaCppQwen3VlRuntime
import com.kenznguyen.smartadskip.ai.Qwen3VlRuntimeConfig
import com.kenznguyen.smartadskip.ai.ModelPackageImporter
import com.kenznguyen.smartadskip.detection.AdDetectionRepository
import com.kenznguyen.smartadskip.detection.AdState
import com.kenznguyen.smartadskip.detection.AdStateClassifier
import com.kenznguyen.smartadskip.overlay.OverlayRequestRepository
import com.kenznguyen.smartadskip.overlay.OverlayVisibilityPolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmartAdSkipAccessibilityService : AccessibilityService() {
    private val snapshotPolicy = AccessibilitySnapshotPolicy()
    private val adStateClassifier = AdStateClassifier()
    private val structuralTransitionAnalyzer = StructuralTransitionAnalyzer()
    private val temporalPatternAnalyzer = TemporalPatternAnalyzer()
    private val actionableNodeDetector = ActionableNodeDetector()
    private val actionExecutionPolicy = ActionExecutionPolicy()
    private val autonomousTargetSafetyPolicy = AutonomousTargetSafetyPolicy()
    private val accessibilityNodeMatcher = AccessibilityNodeMatcher()
    private val aiRuntimeConfig = Qwen3VlRuntimeConfig()
    private var qwenRuntime: LlamaCppQwen3VlRuntime? = null
    private var aiDecisionEngine: AutonomousAdDecisionEngine? = null
    private val aiServiceLifecycleGate = AiServiceLifecycleGate()
    private var serviceGeneration = 0L
    private val screenshotLifecycle = ScreenshotCaptureLifecycle()
    private val screenshotCaptureThrottle = ScreenshotCaptureThrottle()
    private data class AccessibilityEventObservation(
        val eventType: Int,
        val className: String?
    )
    private val accessibilityEventDebouncer =
        AccessibilityEventDebouncer<AccessibilityEventObservation>(ACCESSIBILITY_EVENT_DEBOUNCE_MILLIS)
    private var accessibilityEventDebounceRunnable: Runnable? = null
    private data class PendingFrame(
        val snapshot: AccessibilitySnapshot,
        val previous: AccessibilitySnapshot?,
        val observedAtMillis: Long
    )
    private val latestFrameCoalescer = LatestFrameCoalescer<PendingFrame>()
    private var screenshotCaptureTimeout: Runnable? = null
    private var pendingFrameDrain: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private var previousSnapshot: AccessibilitySnapshot? = null
    private var lastActionableUiState: Pair<String?, List<StructuralNodeMetadata>>? = null
    private val attemptedControlKeys = mutableSetOf<String>()
    private var serviceScope = newServiceScope()
    private var overlayRoot: LinearLayout? = null
    private var overlayStatus: TextView? = null
    private var overlayCollapsed = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceGeneration = aiServiceLifecycleGate.connect()
        if (serviceScope.coroutineContext[Job]?.isActive != true) {
            serviceScope = newServiceScope()
        }
        resetMonitoringState()
        initializeAiRuntimeIfAvailable(serviceGeneration)
        AccessibilityMonitorRepository.setConnected(true)
        serviceScope.launch {
            OverlayRequestRepository.requested.collect { requested ->
                if (OverlayVisibilityPolicy.shouldShow(true, requested)) showOverlay() else removeOverlay()
            }
        }
        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                AccessibilityMonitorRepository.latestSnapshot,
                AdDetectionRepository.state,
                kotlinx.coroutines.flow.flow {
                    while (true) {
                        emit(Unit)
                        delay(1_000)
                    }
                }
            ) { snapshot, state, _ ->
                val fresh = snapshot != null && AccessibilitySnapshotFreshnessPolicy.isFresh(
                    snapshot.observedAtEpochMillis,
                    System.currentTimeMillis()
                )
                val adState = if (fresh) state.name else "UNKNOWN · STALE"
                "Monitor: CONNECTED\nAd state: $adState\nPackage: ${snapshot?.packageName ?: "—"}"
            }.collect { overlayStatus?.text = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Never retain the framework-owned AccessibilityEvent beyond this callback.
        val observation = AccessibilityEventObservation(
            eventType = event.eventType,
            className = event.className?.toString()
        )
        val isCriticalTransition = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED

        when (val submission = accessibilityEventDebouncer.submit(observation, isCriticalTransition)) {
            is AccessibilityEventDebouncer.Submission.Immediate -> {
                accessibilityEventDebounceRunnable?.let(mainHandler::removeCallbacks)
                accessibilityEventDebounceRunnable = null
                processAccessibilityEvent(submission.value)
            }
            is AccessibilityEventDebouncer.Submission.Deferred -> {
                accessibilityEventDebounceRunnable?.let(mainHandler::removeCallbacks)
                val runnable = Runnable {
                    accessibilityEventDebounceRunnable = null
                    val latest = accessibilityEventDebouncer.flush() ?: return@Runnable
                    processAccessibilityEvent(latest)
                }
                accessibilityEventDebounceRunnable = runnable
                mainHandler.postDelayed(runnable, submission.delayMillis)
            }
        }
    }

    private fun processAccessibilityEvent(observation: AccessibilityEventObservation) {
        // event.packageName identifies the event source, not necessarily the active app.
        // Use the active window root as the foreground-package source of truth.
        val activeRoot = runCatching { rootInActiveWindow }.getOrNull()
        val activePackageName = runCatching { activeRoot?.packageName?.toString() }.getOrNull()
        val foreground = validateForegroundWindow(TARGET_PACKAGE)
        if (!foreground.isValid) {
            Log.d(TAG, "foreground_window_rejected stage=event reason=${foreground.status.name.lowercase()}")
            return
        }
        if (!adStateClassifier.isRelevantPackage(activePackageName)) return

        val nodes = runCatching { collectStructuralNodes(activeRoot) }
            .getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val isWindowStateChanged = observation.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val baseSnapshot = snapshotPolicy.create(
            packageName = activePackageName,
            eventType = observation.eventType,
            // Retain source-view class for existing structural diagnostics; it is not an Activity.
            windowClassName = observation.className,
            activityClassName = observation.className,
            isWindowStateChanged = isWindowStateChanged,
            nodeCount = nodes.size,
            clickableNodeCount = nodes.count { it.clickable },
            observedAtEpochMillis = now,
            structuralNodes = nodes
        )
        val snapshot = baseSnapshot.copy(
            structuralTransition = structuralTransitionAnalyzer.analyze(previousSnapshot, baseSnapshot, now),
            temporalPattern = temporalPatternAnalyzer.record(baseSnapshot),
            actionableNodes = actionableNodeDetector.detect(baseSnapshot, now)
        )
        val priorSnapshot = previousSnapshot
        previousSnapshot = snapshot
        val state = adStateClassifier.classify(snapshot, now, priorSnapshot)
        AdDetectionRepository.publish(state)
        AccessibilityMonitorRepository.publish(snapshot)
        initializeAiRuntimeIfAvailable(serviceGeneration)
        captureAndAttemptAction(snapshot, priorSnapshot, now)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        aiServiceLifecycleGate.disconnect()
        serviceScope.cancel()
        removeOverlay()
        val runtimeToClose = qwenRuntime
        qwenRuntime = null
        aiDecisionEngine = null
        runtimeToClose?.close()
        AiRuntimeDashboardStatus.setStopped()
        resetMonitoringState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        aiServiceLifecycleGate.disconnect()
        removeOverlay()
        serviceScope.cancel()
        val runtimeToClose = qwenRuntime
        qwenRuntime = null
        aiDecisionEngine = null
        runtimeToClose?.close()
        AiRuntimeDashboardStatus.setStopped()
        resetMonitoringState()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayRoot != null) return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 28, 32, 38))
                cornerRadius = 18f
            }
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val title = TextView(this).apply {
            text = "SMART AD SKIP  ·  DRAG"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(4, 12, 8, 12)
        }
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val status = TextView(this).apply {
            text = "Monitor: CONNECTED\nAd state: UNKNOWN"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(4, 4, 4, 8)
        }
        val collapseButton = Button(this).apply {
            text = "Collapse"
            setOnClickListener {
                overlayCollapsed = !overlayCollapsed
                details.visibility = if (overlayCollapsed) View.GONE else View.VISIBLE
                text = if (overlayCollapsed) "Expand" else "Collapse"
            }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@SmartAdSkipAccessibilityService).apply {
                text = "Close"
                setOnClickListener { OverlayRequestRepository.setRequested(false) }
            })
        }
        header.addView(title)
        header.addView(collapseButton)
        details.addView(status)
        details.addView(actions)
        root.addView(header)
        root.addView(details)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 160
        }
        title.setOnTouchListener(object : View.OnTouchListener {
            private var startRawX = 0f
            private var startRawY = 0f
            private var startX = 0
            private var startY = 0
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawX = event.rawX
                        startRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = (startX - event.rawX + startRawX).toInt().coerceAtLeast(0)
                        params.y = (startY + event.rawY - startRawY).toInt().coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(root, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })
        try {
            windowManager.addView(root, params)
            overlayRoot = root
            overlayStatus = status
            overlayCollapsed = false
        } catch (_: RuntimeException) {
            overlayRoot = null
            overlayStatus = null
        }
    }

    private fun removeOverlay() {
        val root = overlayRoot ?: return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        runCatching { windowManager.removeView(root) }
        overlayRoot = null
        overlayStatus = null
        overlayCollapsed = false
    }

    private fun newServiceScope() = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun resetMonitoringState() {
        accessibilityEventDebounceRunnable?.let(mainHandler::removeCallbacks)
        accessibilityEventDebounceRunnable = null
        accessibilityEventDebouncer.clear()
        previousSnapshot = null
        lastActionableUiState = null
        attemptedControlKeys.clear()
        screenshotCaptureTimeout?.let(mainHandler::removeCallbacks)
        screenshotCaptureTimeout = null
        pendingFrameDrain?.let(mainHandler::removeCallbacks)
        pendingFrameDrain = null
        latestFrameCoalescer.clear()
        screenshotLifecycle.reset()
        screenshotCaptureThrottle.reset()
        temporalPatternAnalyzer.reset()
        AdDetectionRepository.reset()
        AccessibilityMonitorRepository.setConnected(false)
    }


    /**
     * Confirms the captured frame still maps to the exact active application window.
     * The active root package alone is insufficient after dialogs or app switches.
     */
    private fun validateForegroundWindow(
        expectedPackage: String,
        snapshotPackage: String = expectedPackage
    ): ForegroundWindowValidationPolicy.Result {
        val connected = aiServiceLifecycleGate.isCurrent(serviceGeneration)
        val root = runCatching { rootInActiveWindow }.getOrNull()
        if (root == null) {
            return ForegroundWindowValidationPolicy.evaluate(
                expectedPackage = expectedPackage,
                snapshotPackage = snapshotPackage,
                rootPackage = null,
                rootWindowId = null,
                activeWindowId = null,
                activeWindowPackage = null,
                activeWindowType = null,
                applicationWindowType = AccessibilityWindowInfo.TYPE_APPLICATION,
                activeWindowIsActive = false,
                serviceConnected = connected
            )
        }

        val rootPackage = runCatching { root.packageName?.toString() }.getOrNull()
        val rootWindowId = runCatching { root.windowId }.getOrNull()
        val windowList = runCatching { windows }.getOrDefault(emptyList())
        val activeWindow = windowList.firstOrNull { it.id == rootWindowId }
        val result = ForegroundWindowValidationPolicy.evaluate(
            expectedPackage = expectedPackage,
            snapshotPackage = snapshotPackage,
            rootPackage = rootPackage,
            rootWindowId = rootWindowId,
            activeWindowId = activeWindow?.id,
            activeWindowPackage = rootPackage,
            activeWindowType = activeWindow?.type,
            applicationWindowType = AccessibilityWindowInfo.TYPE_APPLICATION,
            activeWindowIsActive = activeWindow?.isActive == true,
            serviceConnected = connected
        )
        windowList.forEach { runCatching { it.recycle() } }
        runCatching { root.recycle() }
        return result
    }

    /**
     * Screenshot failures are fail-closed. The request token prevents late callbacks
     * from an old service lifecycle from processing frames or releasing a newer request.
     */
    private fun captureAndAttemptAction(
        snapshot: AccessibilitySnapshot,
        previous: AccessibilitySnapshot?,
        now: Long
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            logDeny("screenshot", "unsupported_api_level")
            return
        }
        if (snapshot.packageName != TARGET_PACKAGE) {
            logDeny("package", "wrong_snapshot_package")
            return
        }
        if (!AccessibilitySnapshotFreshnessPolicy.isFresh(snapshot.observedAtEpochMillis, now)) {
            logDeny("freshness", "snapshot_stale_before_capture")
            return
        }
        val foreground = validateForegroundWindow(snapshot.packageName.orEmpty())
        if (!foreground.isValid) {
            logDeny("foreground_window", foreground.status.name.lowercase())
            return
        }
        val requestId = screenshotLifecycle.begin() ?: run {
            latestFrameCoalescer.offer(PendingFrame(snapshot, previous, now))
            Log.d(TAG, "frame_coalesced reason=screenshot_or_inference_busy pending=latest_only")
            return
        }
        // Android rejects takeScreenshot requests made less than 1 second apart.
        // Use monotonic time and reserve the interval only after single-flight succeeds.
        val captureRequestAtMillis = SystemClock.elapsedRealtime()
        if (!screenshotCaptureThrottle.tryAcquire(captureRequestAtMillis)) {
            finishScreenshotRequest(requestId)
            logDeny("screenshot", "capture_throttled_min_interval")
            return
        }

        val captureTimeout = Runnable {
            if (screenshotLifecycle.timeoutCapture(requestId)) {
                screenshotCaptureTimeout = null
                Log.w(TAG, "capture_timeout request_id=$requestId state_recovered=true")
                drainLatestFrame()
            }
        }
        screenshotCaptureTimeout = captureTimeout
        mainHandler.postDelayed(captureTimeout, SCREENSHOT_CAPTURE_TIMEOUT_MILLIS)
        Log.i(TAG, "capture_requested request_id=$requestId timeout_ms=$SCREENSHOT_CAPTURE_TIMEOUT_MILLIS")

        ScreenshotRequestGuard.execute(
            request = {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            mainHandler.removeCallbacks(captureTimeout)
                            if (screenshotCaptureTimeout === captureTimeout) {
                                screenshotCaptureTimeout = null
                            }
                            if (!screenshotLifecycle.completeCapture(requestId)) {
                                runCatching { result.hardwareBuffer.close() }
                                logDeny("screenshot", "late_capture_success request_id=$requestId")
                                return
                            }
                            Log.i(TAG, "capture_callback_success request_id=$requestId")

                            var ownershipTransferredToAi = false
                            val bitmap = try {
                                copyScreenshot(result)
                            } catch (_: RuntimeException) {
                                null
                            }
                            if (bitmap == null) {
                                logDeny("screenshot", "screenshot_copy_failed")
                                screenshotLifecycle.finish(requestId)
                                return
                            }
                            try {
                                val foreground = validateForegroundWindow(snapshot.packageName.orEmpty())
                                if (!foreground.isValid) {
                                    logDeny("foreground_window", "capture_callback_${foreground.status.name.lowercase()}")
                                    return
                                }
                                if (screenshotLifecycle.isCurrent(requestId)) {
                                    runAutonomousAiDecision(
                                        bitmap = bitmap,
                                        snapshot = snapshot,
                                        previous = previous,
                                        requestId = requestId
                                    )
                                    ownershipTransferredToAi = true
                                }
                            } catch (_: RuntimeException) {
                                // A failed frame must never reach visual authorization or clicking.
                            } finally {
                                if (!ownershipTransferredToAi) {
                                    bitmap.recycle()
                                    screenshotLifecycle.finish(requestId)
                                }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            mainHandler.removeCallbacks(captureTimeout)
                            if (screenshotCaptureTimeout === captureTimeout) {
                                screenshotCaptureTimeout = null
                            }
                            if (screenshotLifecycle.failCapture(requestId)) {
                                Log.w(TAG, "capture_callback_error request_id=$requestId error_code=$errorCode")
                                drainLatestFrame()
                            } else {
                                logDeny("screenshot", "late_capture_failure request_id=$requestId")
                            }
                        }
                    }
                )
            },
            onFailure = { error ->
                mainHandler.removeCallbacks(captureTimeout)
                if (screenshotCaptureTimeout === captureTimeout) {
                    screenshotCaptureTimeout = null
                }
                screenshotCaptureThrottle.release(captureRequestAtMillis)
                val errorMessage = error.message
                    ?.replace(Regex("\\s+"), " ")
                    ?.take(160)
                    .orEmpty()
                logDeny(
                    "screenshot",
                    "capture_request_failed exception=" + error.javaClass.simpleName + " message=" + errorMessage
                )
                screenshotLifecycle.finish(requestId)
            }
        )
    }

    private fun finishScreenshotRequest(requestId: Long) {
        if (screenshotLifecycle.finish(requestId)) {
            // AI completion may run on Dispatchers.Default; all AccessibilityService work stays on main.
            mainHandler.post { drainLatestFrame() }
        }
    }

    /**
     * Dispatches only the newest deferred frame after the previous capture/inference owner releases.
     * The one-slot mailbox is bounded and stale or non-foreground frames are discarded.
     */
    private fun drainLatestFrame() {
        if (!latestFrameCoalescer.hasPending()) return
        pendingFrameDrain?.let(mainHandler::removeCallbacks)
        val waitMillis = screenshotCaptureThrottle.millisUntilAllowed(SystemClock.elapsedRealtime())
        if (waitMillis > 0L) {
            val drain = Runnable {
                pendingFrameDrain = null
                drainLatestFrame()
            }
            pendingFrameDrain = drain
            mainHandler.postDelayed(drain, waitMillis)
            return
        }
        val pending = latestFrameCoalescer.poll() ?: return
        val activePackage = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
        val now = System.currentTimeMillis()
        if (activePackage != TARGET_PACKAGE || pending.snapshot.packageName != TARGET_PACKAGE ||
            !AccessibilitySnapshotFreshnessPolicy.isFresh(pending.snapshot.observedAtEpochMillis, now)
        ) {
            Log.d(TAG, "frame_coalesced_drop reason=stale_or_target_not_foreground")
            return
        }
        Log.d(TAG, "frame_coalesced_dispatch age_ms=${(now - pending.snapshot.observedAtEpochMillis).coerceAtLeast(0L)}")
        captureAndAttemptAction(pending.snapshot, pending.previous, now)
    }

    private fun initializeAiRuntimeIfAvailable(generation: Long) {
        val modelDirectory = File(filesDir, "models/${ModelPackageImporter.MODEL_DIRECTORY}")
        val modelFile = File(modelDirectory, ModelPackageImporter.MODEL_FILE)
        val projectorFile = File(modelDirectory, ModelPackageImporter.PROJECTOR_FILE)
        if (!modelFile.isFile || !projectorFile.isFile) {
            AiRuntimeDashboardStatus.setNotImported()
            return
        }
        if (!aiServiceLifecycleGate.tryBeginInitialization(generation)) return

        AiRuntimeDashboardStatus.setLoading(aiRuntimeConfig)
        val runtime = LlamaCppQwen3VlRuntime(aiRuntimeConfig)
        qwenRuntime = runtime
        serviceScope.launch {
            try {
                runtime.load(modelFile, projectorFile)
                if (!aiServiceLifecycleGate.isCurrent(generation) ||
                    qwenRuntime !== runtime || !runtime.isModelReady
                ) {
                    runtime.close()
                    return@launch
                }
                AiRuntimeDashboardStatus.setReady()
                aiDecisionEngine = AutonomousAdDecisionEngine(
                    runtime,
                    inferenceTimeoutMillis = aiRuntimeConfig.inferenceTimeoutMillis
                )
                Log.i(TAG, "ai_runtime_ready engine=qwen3-vl-2b generation=$generation")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                runtime.close()
                throw cancelled
            } catch (error: Exception) {
                runtime.close()
                if (aiServiceLifecycleGate.isCurrent(generation) && qwenRuntime === runtime) {
                    qwenRuntime = null
                    aiDecisionEngine = null
                    AiRuntimeDashboardStatus.setFailed("model_load_failed=${error.javaClass.simpleName}")
                    logDeny("ai_runtime", "model_load_failed=${error.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * Qwen3-VL alone selects NEXT_AD, CLOSE, or WAIT. This method packages the current
     * screenshot and Accessibility context, then forwards a validated target to the executor.
     */
    private fun runAutonomousAiDecision(
        bitmap: Bitmap,
        snapshot: AccessibilitySnapshot,
        previous: AccessibilitySnapshot?,
        requestId: Long
    ) {
        val engine = aiDecisionEngine
        if (engine == null) {
            logDeny("ai_decision", "qwen3_vl_not_ready")
            bitmap.recycle()
            screenshotLifecycle.finish(requestId)
            return
        }

        serviceScope.launch(Dispatchers.Default) {
            try {
                // The screenshot and Accessibility tree share this immutable request identity
                // and the event timestamp. Resize before JPEG/JNI to reduce peak memory.
                val originalWidth = bitmap.width
                val originalHeight = bitmap.height
                val scale = minOf(
                    1f,
                    aiRuntimeConfig.maxImageEdge.toFloat() / maxOf(originalWidth, originalHeight)
                )
                val inferenceBitmap = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (originalWidth * scale).toInt().coerceAtLeast(1),
                        (originalHeight * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else bitmap
                val jpeg = ByteArrayOutputStream()
                try {
                    if (!runCatching {
                        inferenceBitmap.compress(Bitmap.CompressFormat.JPEG, 82, jpeg)
                    }.getOrDefault(false)) {
                        logDeny("ai_decision", "screenshot_encode_failed")
                        return@launch
                    }
                } finally {
                    if (inferenceBitmap !== bitmap) inferenceBitmap.recycle()
                }
                val elements = snapshot.structuralNodes.mapIndexedNotNull { index, node ->
                    if (!node.clickable || !node.enabled || !node.visible || !node.bounds.hasArea) return@mapIndexedNotNull null
                    if (node.text.isNullOrBlank() && node.contentDescription.isNullOrBlank()) return@mapIndexedNotNull null
                    AdUiElement(
                        id = "node-$index",
                        bounds = node.bounds,
                        description = node.text?.takeIf { it.isNotBlank() }
                            ?: node.contentDescription?.takeIf { it.isNotBlank() },
                        clickable = true
                    )
                }.take(MAX_AI_ELEMENTS)
                val frame = AdFrameContext(
                    packageName = snapshot.packageName.orEmpty(),
                    observedAtEpochMillis = snapshot.observedAtEpochMillis,
                    screenWidth = originalWidth,
                    screenHeight = originalHeight,
                    imageJpeg = jpeg.toByteArray(),
                    elements = elements,
                    frameId = requestId
                )
                val decision = engine.decide(frame)
                withContext(Dispatchers.Main.immediate) {
                    if (!screenshotLifecycle.isCurrent(requestId)) {
                        logDeny("ai_decision", "stale_ai_callback")
                        return@withContext
                    }
                    when (decision) {
                        is AutonomousDecision.Wait -> {
                            AiRuntimeDashboardStatus.recordDecision(
                                action = "WAIT",
                                reason = decision.reason,
                                frameId = frame.frameId,
                                observedAtEpochMillis = frame.observedAtEpochMillis
                            )
                            if (decision.reason == "inference_failed") {
                                val failedRuntime = qwenRuntime
                                qwenRuntime = null
                                aiDecisionEngine = null
                                failedRuntime?.close()
                                AiRuntimeDashboardStatus.setFailed("native_inference_failed")
                            }
                            logDeny("ai_decision", decision.reason)
                        }
                        is AutonomousDecision.Act -> {
                            AiRuntimeDashboardStatus.recordDecision(
                                action = decision.action.name,
                                reason = "model_selected_target",
                                frameId = frame.frameId,
                                observedAtEpochMillis = frame.observedAtEpochMillis
                            )
                            val index = decision.target.id.removePrefix("node-").toIntOrNull()
                            val targetNode = index?.let(snapshot.structuralNodes::getOrNull)
                            val action = when (decision.action) {
                                AdAction.NEXT_AD -> VisualActionType.NEXT_AD
                                AdAction.CLOSE -> VisualActionType.CLOSE
                                AdAction.WAIT -> null
                            }
                            if (targetNode == null || action == null) {
                                logDeny("ai_decision", "invalid_ai_target_mapping")
                            } else {
                                authorizeAiDecision(
                                    snapshot = snapshot,
                                    previous = previous,
                                    targetNode = targetNode,
                                    action = action,
                                    confidence = decision.confidence,
                                    aiAction = decision.action,
                                    requestId = requestId
                                )
                            }
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AiRuntimeDashboardStatus.recordDecision(
                    action = "WAIT",
                    reason = "inference_failed=${error.javaClass.simpleName}",
                    frameId = requestId,
                    observedAtEpochMillis = snapshot.observedAtEpochMillis
                )
                logDeny("ai_decision", "inference_failed=${error.javaClass.simpleName}")
            } finally {
                bitmap.recycle()
                screenshotLifecycle.finish(requestId)
            }
        }
    }

    private fun authorizeAiDecision(
        snapshot: AccessibilitySnapshot,
        previous: AccessibilitySnapshot?,
        targetNode: StructuralNodeMetadata,
        action: VisualActionType,
        confidence: Float,
        aiAction: AdAction,
        requestId: Long
    ) {
        if (!screenshotLifecycle.isCurrent(requestId)) {
            logDeny("ai_authorization", "stale_screenshot_request")
            return
        }
        val foreground = validateForegroundWindow(snapshot.packageName.orEmpty())
        if (!foreground.isValid) {
            logDeny("foreground_window", "ai_authorization_${foreground.status.name.lowercase()}")
            return
        }
        if (!isAiFrameFresh(snapshot)) {
            logDeny("ai_authorization", "ai_frame_stale_after_inference")
            return
        }
        if (action !in APPROVED_ACTIONS || confidence < AutonomousAdDecisionEngine.DEFAULT_MINIMUM_CONFIDENCE) {
            logDeny("ai_authorization", "invalid_model_action_or_confidence")
            return
        }
        if (!autonomousTargetSafetyPolicy.canExecute(targetNode, aiAction)) {
            logDeny("ai_authorization", "protected_target_label")
            return
        }
        Log.i(TAG, "ai_decision action=$action confidence=$confidence")
        val detectedState = adStateClassifier.classify(
            snapshot,
            System.currentTimeMillis(),
            previous,
            hasDetectedAction = true
        )
        AdDetectionRepository.publish(detectedState)
        attemptAuthorizedLiveClick(snapshot, targetNode, action, detectedState, requestId)
    }

    /**
     * Always close the HardwareBuffer, including when wrapping or copying fails.
     * The returned software bitmap is owned by the caller.
     */
    private fun copyScreenshot(result: ScreenshotResult): Bitmap? {
        var hardwareBitmap: Bitmap? = null
        return try {
            hardwareBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
            hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
        } finally {
            runCatching { hardwareBitmap?.recycle() }
            runCatching { result.hardwareBuffer.close() }
        }
    }

    /**
     * AI-selected targets still require semantic Accessibility re-identification,
     * freshness, protected-target veto, and final Click Authorization.
     */
    private fun isAiFrameFresh(snapshot: AccessibilitySnapshot): Boolean {
        val ageMillis = System.currentTimeMillis() - snapshot.observedAtEpochMillis
        return ageMillis in 0L..AutonomousAdDecisionEngine.MAX_FRAME_AGE_MILLIS
    }

    private fun attemptAuthorizedLiveClick(
        snapshot: AccessibilitySnapshot,
        targetNode: StructuralNodeMetadata,
        action: VisualActionType,
        adState: AdState,
        requestId: Long
    ) {
        if (snapshot.packageName != TARGET_PACKAGE) {
            logDeny("package", "wrong_snapshot_package_before_click")
            return
        }
        val foreground = validateForegroundWindow(snapshot.packageName.orEmpty())
        if (!foreground.isValid) {
            logDeny("foreground_window", "live_lookup_${foreground.status.name.lowercase()}")
            return
        }
        if (!isAiFrameFresh(snapshot)) {
            logDeny("freshness", "snapshot_stale_before_live_lookup")
            return
        }
        if (action !in APPROVED_ACTIONS) {
            logDeny("click_authorization", "unsupported_action=$action")
            return
        }

        val uiState = snapshot.windowClassName to snapshot.structuralNodes
        if (uiState != lastActionableUiState) {
            lastActionableUiState = uiState
            attemptedControlKeys.clear()
        }
        val identityKey = listOf(
            action.name,
            targetNode.viewClassName.orEmpty(),
            targetNode.text.orEmpty().trim().lowercase(),
            targetNode.contentDescription.orEmpty().trim().lowercase()
        ).joinToString("|")
        if (identityKey in attemptedControlKeys) {
            logDeny("one_attempt_guard", "control_already_attempted")
            return
        }

        val root = rootInActiveWindow
        if (root == null) {
            logDeny("live_accessibility", "active_window_root_missing")
            return
        }
        if (root.packageName?.toString() != TARGET_PACKAGE) {
            logDeny("live_accessibility", "active_window_package_mismatch")
            root.recycle()
            return
        }

        val pending = ArrayDeque<AccessibilityNodeInfo>()
        val liveNodes = ArrayList<AccessibilityNodeInfo>(MAX_NODES_PER_EVENT)
        val liveMetadata = ArrayList<StructuralNodeMetadata>(MAX_NODES_PER_EVENT)
        pending.addLast(root)
        try {
            while (pending.isNotEmpty() && liveNodes.size < MAX_NODES_PER_EVENT) {
                val node = pending.removeFirst()
                val rect = Rect()
                node.getBoundsInScreen(rect)
                liveNodes += node
                liveMetadata += StructuralNodeMetadata(
                    viewClassName = node.className?.toString(),
                    resourceId = null,
                    clickable = node.isClickable,
                    bounds = ScreenBounds(rect.left, rect.top, rect.right, rect.bottom),
                    enabled = node.isEnabled,
                    visible = node.isVisibleToUser,
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    childCount = node.childCount
                )
                for (index in 0 until node.childCount) {
                    if (liveNodes.size + pending.size >= MAX_NODES_PER_EVENT) break
                    node.getChild(index)?.let(pending::addLast)
                }
            }

            val selectedIndex = accessibilityNodeMatcher.findUniqueMatch(targetNode, liveMetadata)
            if (selectedIndex == null) {
                logDeny("live_accessibility", "no_unique_live_node_match")
                return
            }
            val selected = liveNodes[selectedIndex]
            if (!screenshotLifecycle.isCurrent(requestId) || snapshot.packageName != TARGET_PACKAGE || !isAiFrameFresh(snapshot)) {
                logDeny("freshness", "frame_invalidated_or_stale_at_final_click_gate")
                return
            }
            if (!selected.isVisibleToUser || !selected.isEnabled || !selected.isClickable) {
                logDeny("live_accessibility", "selected_node_not_visible_enabled_clickable")
                return
            }
            val liveSafetyAction = when (action) {
                VisualActionType.NEXT_AD -> AdAction.NEXT_AD
                VisualActionType.CLOSE -> AdAction.CLOSE
                else -> AdAction.WAIT
            }
            if (!autonomousTargetSafetyPolicy.canExecute(liveMetadata[selectedIndex], liveSafetyAction)) {
                logDeny("live_accessibility", "live_target_safety_veto")
                return
            }
            val finalForeground = validateForegroundWindow(snapshot.packageName.orEmpty())
            if (!finalForeground.isValid) {
                logDeny("foreground_window", "final_click_${finalForeground.status.name.lowercase()}")
                return
            }
            if (!actionExecutionPolicy.canClick(adState, action)) {
                logDeny("click_authorization", "policy_denied_state=$adState action=$action")
                return
            }

            attemptedControlKeys += identityKey
            val clicked = runCatching {
                selected.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }.getOrElse {
                logDeny("perform_action", "exception=${it.javaClass.simpleName}")
                false
            }
            if (clicked) {
                Log.i(TAG, "click_dispatched action=$action source_live_accessibility=true")
            } else {
                logDeny("perform_action", "accessibility_action_click_returned_false")
            }
        } finally {
            liveNodes.forEach { it.recycle() }
            while (pending.isNotEmpty()) pending.removeFirst().recycle()
        }
    }

    private fun logDeny(stage: String, reason: String) {
        Log.i(TAG, "click_denied stage=$stage reason=$reason")
    }

    private fun collectStructuralNodes(root: AccessibilityNodeInfo?): List<StructuralNodeMetadata> {
        if (root == null) return emptyList()
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        val collected = ArrayList<StructuralNodeMetadata>(MAX_NODES_PER_EVENT)
        pending.addLast(root)
        while (pending.isNotEmpty() && collected.size < MAX_NODES_PER_EVENT) {
            val node = pending.removeFirst()
            try {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                collected += StructuralNodeMetadata(
                    viewClassName = node.className?.toString(),
                    resourceId = null,
                    clickable = node.isClickable,
                    bounds = ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom),
                    enabled = node.isEnabled,
                    visible = node.isVisibleToUser,
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    childCount = node.childCount
                )
                for (index in 0 until node.childCount) {
                    if (collected.size + pending.size >= MAX_NODES_PER_EVENT) break
                    node.getChild(index)?.let(pending::addLast)
                }
            } finally {
                node.recycle()
            }
        }
        while (pending.isNotEmpty()) pending.removeFirst().recycle()
        return collected
    }

    private companion object {
        const val TAG = "SmartAdSkip"
        const val MAX_NODES_PER_EVENT = 200
        const val MAX_AI_ELEMENTS = 64
        const val SCREENSHOT_CAPTURE_TIMEOUT_MILLIS = 5_000L
        const val ACCESSIBILITY_EVENT_DEBOUNCE_MILLIS = 120L
        const val TARGET_PACKAGE = "com.funnyvideo.cgame.fruitblast"
        val APPROVED_ACTIONS = setOf(VisualActionType.NEXT_AD, VisualActionType.CLOSE)
    }
}
