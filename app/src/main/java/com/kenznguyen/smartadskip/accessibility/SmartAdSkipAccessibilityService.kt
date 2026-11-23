package com.kenznguyen.smartadskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kenznguyen.smartadskip.detection.AdDetectionRepository
import com.kenznguyen.smartadskip.detection.AdState
import com.kenznguyen.smartadskip.detection.AdStateClassifier
import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class SmartAdSkipAccessibilityService : AccessibilityService() {
    private val snapshotPolicy = AccessibilitySnapshotPolicy()
    private val adStateClassifier = AdStateClassifier()
    private val structuralTransitionAnalyzer = StructuralTransitionAnalyzer()
    private val temporalPatternAnalyzer = TemporalPatternAnalyzer()
    private val actionableNodeDetector = ActionableNodeDetector()
    private val actionExecutionPolicy = ActionExecutionPolicy()
    private val visualActionDetector = VisualActionDetector()
    private val visualActionTemporalVerifier = VisualActionTemporalVerifier()
    private val screenshotInFlight = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }
    private var previousSnapshot: AccessibilitySnapshot? = null
    private var lastActionableUiState: Pair<String?, List<StructuralNodeMetadata>>? = null
    private val attemptedControlKeys = mutableSetOf<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        resetMonitoringState()
        AccessibilityMonitorRepository.setConnected(true)
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
        resetMonitoringState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        resetMonitoringState()
        super.onDestroy()
    }

    private fun resetMonitoringState() {
        previousSnapshot = null
        lastActionableUiState = null
        attemptedControlKeys.clear()
        screenshotInFlight.set(false)
        visualActionTemporalVerifier.reset()
        temporalPatternAnalyzer.reset()
        AdDetectionRepository.reset()
        AccessibilityMonitorRepository.setConnected(false)
    }

    /**
     * Screenshot-based recognition is fail-closed on API < 30 and on capture errors.
     * Frames remain in memory and are recycled after processing.
     */
    private fun captureAndAttemptVisualClick(
        snapshot: AccessibilitySnapshot,
        previous: AccessibilitySnapshot?,
        now: Long
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (snapshot.packageName != TARGET_PACKAGE) return
        if (now - snapshot.observedAtEpochMillis !in 0..MAX_SNAPSHOT_AGE_MILLIS) return
        if (!screenshotInFlight.compareAndSet(false, true)) return

        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                try {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                        result.hardwareBuffer,
                        result.colorSpace
                    ) ?: return
                    val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBitmap.recycle()
                    result.hardwareBuffer.close()
                    try {
                        val detected = findVisualAction(snapshot, bitmap)
                        val visualMatch = detected?.second
                            ?: VisualActionMatch(VisualActionType.UNKNOWN, 0f)
                        val confirmedAction = visualActionTemporalVerifier.observe(
                            visualMatch,
                            System.currentTimeMillis()
                        )
                        if (detected != null && confirmedAction != null) {
                            val verifiedState = adStateClassifier.classify(
                                snapshot, System.currentTimeMillis(), previous,
                                hasVerifiedVisualAction = true
                            )
                            AdDetectionRepository.publish(verifiedState)
                            attemptVerifiedLiveClick(snapshot, detected.first, confirmedAction, verifiedState)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                } catch (_: RuntimeException) {
                    // Screenshot decoding or processing failure must never trigger a click.
                } finally {
                    screenshotInFlight.set(false)
                }
            }

            override fun onFailure(errorCode: Int) {
                screenshotInFlight.set(false)
            }
        })
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
        if (System.currentTimeMillis() - snapshot.observedAtEpochMillis !in 0..MAX_SNAPSHOT_AGE_MILLIS) return
        if (type !in APPROVED_VISUAL_ACTIONS) return
        if (!actionExecutionPolicy.canClick(
                adState,
                snapshot.windowClassName,
                previousSnapshot?.windowClassName,
                currentHasRewardedMarker = false,
                previousHasRewardedMarker = false
            )
        ) return

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
        const val MAX_SNAPSHOT_AGE_MILLIS = 15_000L
        const val TARGET_PACKAGE = "com.funnyvideo.cgame.fruitblast"
        val APPROVED_VISUAL_ACTIONS = setOf(VisualActionType.NEXT_AD, VisualActionType.CLOSE)
    }
}
