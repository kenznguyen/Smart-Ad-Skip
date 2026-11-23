package com.kenznguyen.smartadskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
    private val visualActionTemporalVerifier = VisualActionTemporalVerifier()
    private val screenshotLifecycle = ScreenshotCaptureLifecycle()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private var previousSnapshot: AccessibilitySnapshot? = null
    private var lastActionableUiState: Pair<String?, List<StructuralNodeMetadata>>? = null
    private val attemptedControlKeys = mutableSetOf<String>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayRoot: LinearLayout? = null
    private var overlayStatus: TextView? = null
    private var overlayCollapsed = false

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
                AdDetectionRepository.state
            ) { snapshot, state ->
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
        captureAndAttemptVisualClick(snapshot, priorSnapshot, now)
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
            text = "Monitor: CONNECTED\\nAd state: UNKNOWN"
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
            private var startRawY = 0f
            private var startY = 0
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawY = event.rawY
                        startY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
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

    private fun resetMonitoringState() {
        previousSnapshot = null
        lastActionableUiState = null
        attemptedControlKeys.clear()
        screenshotLifecycle.reset()
        visualActionTemporalVerifier.reset()
        temporalPatternAnalyzer.reset()
        AdDetectionRepository.reset()
        AccessibilityMonitorRepository.setConnected(false)
    }

    /**
     * Screenshot failures are fail-closed. The request token prevents late callbacks
     * from an old service lifecycle from processing frames or releasing a newer request.
     */
    private fun captureAndAttemptVisualClick(
        snapshot: AccessibilitySnapshot,
        previous: AccessibilitySnapshot?,
        now: Long
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (snapshot.packageName != TARGET_PACKAGE) return
        if (!AccessibilitySnapshotFreshnessPolicy.isFresh(snapshot.observedAtEpochMillis, now)) return
        val requestId = screenshotLifecycle.begin() ?: return

        ScreenshotRequestGuard.execute(
            request = {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(result: ScreenshotResult) {
                            try {
                                val bitmap = copyScreenshot(result) ?: return
                                try {
                                    if (!screenshotLifecycle.isCurrent(requestId)) return
                                    val detected = findVisualAction(snapshot, bitmap)
                                    val visualMatch = detected?.second
                                        ?: VisualActionMatch(VisualActionType.UNKNOWN, 0f)
                                    val confirmedAction = visualActionTemporalVerifier.observe(
                                        visualMatch,
                                        System.currentTimeMillis()
                                    )
                                    if (detected != null && confirmedAction != null) {
                                        val verifiedState = adStateClassifier.classify(
                                            snapshot,
                                            System.currentTimeMillis(),
                                            previous,
                                            hasVerifiedVisualAction = true
                                        )
                                        AdDetectionRepository.publish(verifiedState)
                                        attemptVerifiedLiveClick(
                                            snapshot,
                                            detected.first,
                                            confirmedAction,
                                            verifiedState
                                        )
                                    }
                                } finally {
                                    bitmap.recycle()
                                }
                            } catch (_: RuntimeException) {
                                // A failed frame must never reach visual authorization or clicking.
                            } finally {
                                screenshotLifecycle.finish(requestId)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            screenshotLifecycle.finish(requestId)
                        }
                    }
                )
            },
            onFailure = { screenshotLifecycle.finish(requestId) }
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

    private fun findVisualAction(
        snapshot: AccessibilitySnapshot,
        bitmap: Bitmap
    ): Pair<ScreenBounds, VisualActionMatch>? {
        val candidates = snapshot.structuralNodes
            .asSequence()
            .filter { it.clickable && it.enabled && it.visible && it.bounds.hasArea }
            .take(MAX_NODES_PER_EVENT)
        val matches = candidates.mapNotNull { candidate ->
            val left = candidate.bounds.left.coerceIn(0, bitmap.width)
            val top = candidate.bounds.top.coerceIn(0, bitmap.height)
            val right = candidate.bounds.right.coerceIn(0, bitmap.width)
            val bottom = candidate.bounds.bottom.coerceIn(0, bitmap.height)
            if (right <= left || bottom <= top) return@mapNotNull null
            val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
            try {
                val pixels = IntArray(crop.width * crop.height)
                crop.getPixels(pixels, 0, crop.width, 0, 0, crop.width, crop.height)
                val match = visualActionDetector.detect(VisualFrame(crop.width, crop.height, pixels))
                if (match.type == VisualActionType.UNKNOWN) null
                else Triple(candidate.bounds, match, match.confidence)
            } finally {
                crop.recycle()
            }
        }.toList()

        return matches.maxByOrNull { it.third }?.let { it.first to it.second }
    }

    private fun attemptVerifiedLiveClick(
        snapshot: AccessibilitySnapshot,
        targetBounds: ScreenBounds,
        type: VisualActionType,
        adState: AdState
    ) {
        if (snapshot.packageName != TARGET_PACKAGE) return
        if (!AccessibilitySnapshotFreshnessPolicy.isFresh(snapshot.observedAtEpochMillis, System.currentTimeMillis())) return
        if (type !in APPROVED_VISUAL_ACTIONS) return
        if (!actionExecutionPolicy.canClick(adState)) return

        val uiState = snapshot.windowClassName to snapshot.structuralNodes
        if (uiState != lastActionableUiState) {
            lastActionableUiState = uiState
            attemptedControlKeys.clear()
        }
        val key = "${type.name}|${targetBounds.left}|${targetBounds.top}|${targetBounds.right}|${targetBounds.bottom}"
        if (key in attemptedControlKeys) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != TARGET_PACKAGE) {
            root.recycle()
            return
        }

        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.addLast(root)
        var selected: AccessibilityNodeInfo? = null
        try {
            var visited = 0
            while (pending.isNotEmpty() && visited < MAX_NODES_PER_EVENT && selected == null) {
                val node = pending.removeFirst()
                visited++
                val rect = Rect()
                node.getBoundsInScreen(rect)
                val bounds = ScreenBounds(rect.left, rect.top, rect.right, rect.bottom)
                val matches = bounds == targetBounds &&
                    node.isClickable && node.isEnabled && node.isVisibleToUser && bounds.hasArea
                val childCount = node.childCount
                for (index in 0 until childCount) {
                    if (pending.size + visited >= MAX_NODES_PER_EVENT) break
                    node.getChild(index)?.let(pending::addLast)
                }
                if (matches) selected = node else node.recycle()
            }

            if (selected != null) {
                attemptedControlKeys += key
                selected?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } finally {
            selected?.recycle()
            while (pending.isNotEmpty()) pending.removeFirst().recycle()
        }
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
                    visible = node.isVisibleToUser
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
        const val MAX_NODES_PER_EVENT = 200
        const val TARGET_PACKAGE = "com.funnyvideo.cgame.fruitblast"
        val APPROVED_VISUAL_ACTIONS = setOf(VisualActionType.NEXT_AD, VisualActionType.CLOSE)
    }
}
