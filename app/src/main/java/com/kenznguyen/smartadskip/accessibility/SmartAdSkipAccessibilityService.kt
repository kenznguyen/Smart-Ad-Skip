package com.kenznguyen.smartadskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
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
import android.view.accessibility.AccessibilityNodeInfo
import com.kenznguyen.smartadskip.detection.AdDetectionRepository
import com.kenznguyen.smartadskip.detection.AdState
import com.kenznguyen.smartadskip.detection.AdStateClassifier
import com.kenznguyen.smartadskip.overlay.OverlayRequestRepository
import com.kenznguyen.smartadskip.overlay.OverlayVisibilityPolicy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class SmartAdSkipAccessibilityService : AccessibilityService() {
    private val snapshotPolicy = AccessibilitySnapshotPolicy()
    private val adStateClassifier = AdStateClassifier()
    private val structuralTransitionAnalyzer = StructuralTransitionAnalyzer()
    private val temporalPatternAnalyzer = TemporalPatternAnalyzer()
    private val actionableNodeDetector = ActionableNodeDetector()
    private val actionExecutionPolicy = ActionExecutionPolicy()
    private val visualActionDetector = VisualActionDetector()
    private val ocrActionPolicy = OcrActionPolicy()
    private val accessibilityNodeMatcher = AccessibilityNodeMatcher()
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val screenshotLifecycle = ScreenshotCaptureLifecycle()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private var previousSnapshot: AccessibilitySnapshot? = null
    private var lastActionableUiState: Pair<String?, List<StructuralNodeMetadata>>? = null
    private val attemptedControlKeys = mutableSetOf<String>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayRoot: LinearLayout? = null
    private var overlayStatus: TextView? = null
    private var overlayDetails: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var overlayWindowManager: WindowManager? = null
    private var overlayCollapsed = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        resetMonitoringState()
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
        if (!adStateClassifier.isRelevantPackage(event.packageName?.toString())) return
        val nodes = runCatching { collectStructuralNodes(rootInActiveWindow) }
            .getOrDefault(emptyList())
        val now = System.currentTimeMillis()
        val baseSnapshot = snapshotPolicy.create(
            packageName = event.packageName?.toString(),
            eventType = event.eventType,
            windowClassName = event.className,
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
        captureAndAttemptAction(snapshot, priorSnapshot, now)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        removeOverlay()
        resetMonitoringState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeOverlay()
        serviceScope.cancel()
        runCatching { textRecognizer.close() }
        resetMonitoringState()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayRoot != null) return
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val preferences = getSharedPreferences("floating_overlay", MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 8, 12, 8)
            background = GradientDrawable().apply {
                setColor(Color.argb(238, 28, 32, 38))
                cornerRadius = 24f
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "SMART AD SKIP"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(8, 10, 12, 10)
            isClickable = true
        }
        val expandButton = Button(this).apply {
            text = "Open"
            setOnClickListener { setOverlayCollapsed(false) }
        }
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6, 2, 6, 4)
        }
        val status = TextView(this).apply {
            text = "Monitor: CONNECTED\\nAd state: UNKNOWN\\nClose detection: CLOSE only\\nClick authorization: Strict Safety"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(4, 4, 4, 8)
        }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        controls.addView(Button(this@SmartAdSkipAccessibilityService).apply {
            text = "Minimize"
            setOnClickListener { setOverlayCollapsed(true) }
        })
        controls.addView(Button(this@SmartAdSkipAccessibilityService).apply {
            text = "Hide"
            setOnClickListener { OverlayRequestRepository.setRequested(false) }
        })
        header.addView(title)
        header.addView(expandButton)
        details.addView(status)
        details.addView(controls)
        root.addView(header)
        root.addView(details)

        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - 220).coerceAtLeast(0)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt("x", maxX)
            y = preferences.getInt("y", 160).coerceAtLeast(0)
        }
        title.setOnTouchListener(object : View.OnTouchListener {
            private var rawStartX = 0f
            private var rawStartY = 0f
            private var initialX = 0
            private var initialY = 0
            private var moved = false
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        rawStartX = event.rawX
                        rawStartY = event.rawY
                        initialX = params.x
                        initialY = params.y
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - rawStartX).toInt()
                        val dy = (event.rawY - rawStartY).toInt()
                        if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > 8) moved = true
                        params.x = (initialX + dx).coerceAtLeast(0)
                        params.y = (initialY + dy).coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(root, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            val displayWidth = resources.displayMetrics.widthPixels
                            val overlayWidth = root.width.takeIf { it > 0 } ?: 220
                            val maxPosition = (displayWidth - overlayWidth).coerceAtLeast(0)
                            val edge = OverlayVisibilityPolicy.nearestEdge(params.x, maxPosition)
                            params.x = if (edge == OverlayVisibilityPolicy.Edge.LEFT) 0 else maxPosition
                            val displayHeight = resources.displayMetrics.heightPixels
                            val overlayHeight = root.height.takeIf { it > 0 } ?: 80
                            params.y = OverlayVisibilityPolicy.clampPosition(params.y, displayHeight - overlayHeight)
                            runCatching { windowManager.updateViewLayout(root, params) }
                            preferences.edit().putInt("x", params.x).putInt("y", params.y).apply()
                        } else if (overlayCollapsed) {
                            setOverlayCollapsed(false)
                        }
                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> return true
                }
                return false
            }
        })
        try {
            windowManager.addView(root, params)
            overlayRoot = root
            overlayStatus = status
            overlayDetails = details
            overlayParams = params
            overlayWindowManager = windowManager
            setOverlayCollapsed(preferences.getBoolean("collapsed", true))
        } catch (_: RuntimeException) {
            overlayRoot = null
            overlayStatus = null
            overlayDetails = null
            overlayParams = null
            overlayWindowManager = null
        }
    }

    private fun setOverlayCollapsed(collapsed: Boolean) {
        overlayCollapsed = collapsed
        overlayDetails?.visibility = if (collapsed) View.GONE else View.VISIBLE
        overlayRoot?.let { root ->
            (root.getChildAt(0) as? LinearLayout)?.getChildAt(1)?.let { button ->
                (button as? Button)?.text = if (collapsed) "Open" else " "
            }
        }
        getSharedPreferences("floating_overlay", MODE_PRIVATE)
            .edit().putBoolean("collapsed", collapsed).apply()
    }

    private fun removeOverlay() {
        val root = overlayRoot ?: return
        runCatching { overlayWindowManager?.removeView(root) }
        overlayRoot = null
        overlayStatus = null
        overlayDetails = null
        overlayParams = null
        overlayWindowManager = null
        overlayCollapsed = true
    }

    private fun resetMonitoringState() {
        previousSnapshot = null
        lastActionableUiState = null
        attemptedControlKeys.clear()
        screenshotLifecycle.reset()
        temporalPatternAnalyzer.reset()
        AdDetectionRepository.reset()
        AccessibilityMonitorRepository.setConnected(false)
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
        val requestId = screenshotLifecycle.begin() ?: run {
            logDeny("screenshot", "capture_already_in_flight")
            return
        }

        ScreenshotRequestGuard.execute(
            request = {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            var ownershipTransferredToOcr = false
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
                                if (screenshotLifecycle.isCurrent(requestId)) {
                                    // OCR classifies Close/Dismiss/X candidates. Pixel classification
                                    // discovers symbol-only X controls only; neither grants click permission.
                                    runOcrFirstCandidateDiscovery(
                                        bitmap = bitmap,
                                        snapshot = snapshot,
                                        previous = previous,
                                        requestId = requestId
                                    )
                                    ownershipTransferredToOcr = true
                                }
                            } catch (_: RuntimeException) {
                                // A failed frame must never reach visual authorization or clicking.
                            } finally {
                                if (!ownershipTransferredToOcr) {
                                    bitmap.recycle()
                                    screenshotLifecycle.finish(requestId)
                                }
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            logDeny("screenshot", "capture_failed_code=$errorCode")
                            screenshotLifecycle.finish(requestId)
                        }
                    }
                )
            },
            onFailure = {
                logDeny("screenshot", "capture_request_failed")
                screenshotLifecycle.finish(requestId)
            }
        )
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
     * OCR-first Close-only candidate discovery. For NO_TEXT symbol-only X controls,
     * pixel classification discovers the candidate. All candidates still require live
     * Accessibility and final Click Authorization.
     */
    private fun runOcrFirstCandidateDiscovery(
        bitmap: Bitmap,
        snapshot: AccessibilitySnapshot,
        previous: AccessibilitySnapshot?,
        requestId: Long
    ) {
        val image = runCatching { InputImage.fromBitmap(bitmap, 0) }.getOrNull()
        if (image == null) {
            bitmap.recycle()
            logDeny("ocr", "input_image_creation_failed")
            screenshotLifecycle.finish(requestId)
            return
        }

        val task = runCatching { textRecognizer.process(image) }.getOrElse {
            logDeny("ocr", "recognizer_start_failed")
            bitmap.recycle()
            screenshotLifecycle.finish(requestId)
            return
        }
        task.addOnSuccessListener(mainExecutor) { result ->
            try {
                if (!screenshotLifecycle.isCurrent(requestId)) {
                    logDeny("ocr", "stale_screenshot_callback")
                    return@addOnSuccessListener
                }
                val candidate = findOcrFirstCandidate(snapshot, bitmap, result.textBlocks)
                if (candidate != null) {
                    authorizeDetectedAction(
                        snapshot = snapshot,
                        previous = previous,
                        targetNode = candidate.node,
                        action = candidate.action,
                        ocrHint = candidate.ocrHint,
                        requestId = requestId
                    )
                } else {
                    logDeny("candidate", "no_eligible_close_candidate")
                }
            } catch (error: RuntimeException) {
                logDeny("candidate_pipeline", "runtime_exception=${error.javaClass.simpleName}")
            } finally {
                bitmap.recycle()
                screenshotLifecycle.finish(requestId)
            }
        }.addOnFailureListener(mainExecutor) {
            // OCR failure is fail-closed; no visual-only fallback is attempted.
            logDeny("ocr", "recognition_failed")
            bitmap.recycle()
            screenshotLifecycle.finish(requestId)
        }
    }

    private data class DetectedActionCandidate(
        val node: StructuralNodeMetadata,
        val action: VisualActionType,
        val ocrHint: OcrActionHint
    )

    /**
     * OCR labels identify Close/Dismiss/X candidates. Visual classification is consulted
     * only for NO_TEXT regions to discover a symbol-only X control.
     */
    private fun findOcrFirstCandidate(
        snapshot: AccessibilitySnapshot,
        bitmap: Bitmap,
        textBlocks: List<Text.TextBlock>
    ): DetectedActionCandidate? {
        val candidates = snapshot.structuralNodes
            .asSequence()
            .filter { it.clickable && it.enabled && it.visible && it.bounds.hasArea }
            .take(MAX_NODES_PER_EVENT)

        val eligible = candidates.mapNotNull { candidate ->
            val left = candidate.bounds.left.coerceIn(0, bitmap.width)
            val top = candidate.bounds.top.coerceIn(0, bitmap.height)
            val right = candidate.bounds.right.coerceIn(0, bitmap.width)
            val bottom = candidate.bounds.bottom.coerceIn(0, bitmap.height)
            if (right <= left || bottom <= top) {
                logDeny("candidate", "invalid_candidate_bounds")
                return@mapNotNull null
            }

            val target = Rect(left, top, right, bottom)
            val recognizedText = textBlocks
                .filter { block ->
                    val box = block.boundingBox
                    box != null && Rect.intersects(target, box)
                }
                .joinToString(" ") { it.text }
            val hint = ocrActionPolicy.analyze(recognizedText)

            if (hint == OcrActionHint.PROTECTED) {
                logDeny("ocr_policy", "protected_label_detected")
                return@mapNotNull null
            }
            if (hint == OcrActionHint.AMBIGUOUS) {
                logDeny("ocr_policy", "ambiguous_label_detected")
                return@mapNotNull null
            }

            val symbolDiscovery = if (hint == OcrActionHint.NO_TEXT) {
                val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                try {
                    val pixels = IntArray(crop.width * crop.height)
                    crop.getPixels(pixels, 0, crop.width, 0, 0, crop.width, crop.height)
                    visualActionDetector.detect(VisualFrame(crop.width, crop.height, pixels)).type
                } finally {
                    crop.recycle()
                }
            } else {
                VisualActionType.UNKNOWN
            }

            val action = ocrActionPolicy.resolveCandidate(recognizedText, symbolDiscovery)
            if (action == null) {
                if (hint == OcrActionHint.NO_TEXT) {
                    logDeny("symbol_discovery", "no_supported_symbol_found")
                }
                return@mapNotNull null
            }

            DetectedActionCandidate(
                node = candidate,
                action = action,
                ocrHint = hint
            )
        }.toList()

        if (eligible.size > 1) {
            logDeny("candidate", "multiple_eligible_controls_count=${eligible.size}")
            return null
        }
        return eligible.singleOrNull()
    }

    private fun authorizeDetectedAction(
        snapshot: AccessibilitySnapshot,
        previous: AccessibilitySnapshot?,
        targetNode: StructuralNodeMetadata,
        action: VisualActionType,
        ocrHint: OcrActionHint,
        requestId: Long
    ) {
        if (!screenshotLifecycle.isCurrent(requestId)) {
            logDeny("authorization", "stale_screenshot_request")
            return
        }
        if (action !in APPROVED_ACTIONS) {
            logDeny("authorization", "action_not_approved")
            return
        }
        Log.i(TAG, "candidate_detected action=$action source=$ocrHint")
        val detectedState = adStateClassifier.classify(
            snapshot,
            System.currentTimeMillis(),
            previous,
            hasDetectedAction = true
        )
        AdDetectionRepository.publish(detectedState)
        attemptAuthorizedLiveClick(snapshot, targetNode, action, detectedState)
    }

    private fun attemptAuthorizedLiveClick(
        snapshot: AccessibilitySnapshot,
        targetNode: StructuralNodeMetadata,
        action: VisualActionType,
        adState: AdState
    ) {
        if (snapshot.packageName != TARGET_PACKAGE) {
            logDeny("package", "wrong_snapshot_package_before_click")
            return
        }
        if (!AccessibilitySnapshotFreshnessPolicy.isFresh(snapshot.observedAtEpochMillis, System.currentTimeMillis())) {
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
            val selected = selectedIndex?.let(liveNodes::get)
            if (selected == null) {
                logDeny("live_accessibility", "no_unique_live_node_match")
                return
            }
            if (snapshot.packageName != TARGET_PACKAGE ||
                !AccessibilitySnapshotFreshnessPolicy.isFresh(snapshot.observedAtEpochMillis, System.currentTimeMillis())
            ) {
                logDeny("freshness", "snapshot_stale_at_final_click_gate")
                return
            }
            if (!selected.isVisibleToUser || !selected.isEnabled || !selected.isClickable) {
                logDeny("live_accessibility", "selected_node_not_visible_enabled_clickable")
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
        const val TARGET_PACKAGE = "com.funnyvideo.cgame.fruitblast"
        val APPROVED_ACTIONS = setOf(VisualActionType.CLOSE)
    }
}
