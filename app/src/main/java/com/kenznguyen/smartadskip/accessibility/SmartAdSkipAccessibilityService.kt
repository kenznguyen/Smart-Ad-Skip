package com.kenznguyen.smartadskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.kenznguyen.smartadskip.detection.AdDetectionRepository
import com.kenznguyen.smartadskip.detection.AdStateClassifier
import java.util.ArrayDeque

class SmartAdSkipAccessibilityService : AccessibilityService() {
    private val snapshotPolicy = AccessibilitySnapshotPolicy()
    private val adStateClassifier = AdStateClassifier()
    private val structuralTransitionAnalyzer = StructuralTransitionAnalyzer()
    private val temporalPatternAnalyzer = TemporalPatternAnalyzer()
    private val actionableNodeDetector = ActionableNodeDetector()
    private val actionableControlPolicy = ActionableControlPolicy()
    private val actionExecutionPolicy = ActionExecutionPolicy()
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
        // Ignore System UI, screen recorder and other unrelated event sources.
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
        val state = adStateClassifier.classify(snapshot, now, priorSnapshot)
        AdDetectionRepository.publish(state)
        previousSnapshot = snapshot
        AccessibilityMonitorRepository.publish(snapshot)
        attemptApprovedClick(snapshot, now, state, priorSnapshot)
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
        temporalPatternAnalyzer.reset()
        AdDetectionRepository.reset()
        AccessibilityMonitorRepository.setConnected(false)
    }

    /**
     * Clicks at most one explicitly identified navigation/dismiss control per UI state.
     * The live node is revalidated immediately before ACTION_CLICK; no coordinates or
     * inferred visual semantics are used.
     */
    private fun attemptApprovedClick(
        snapshot: AccessibilitySnapshot,
        now: Long,
        adState: com.kenznguyen.smartadskip.detection.AdState,
        previous: AccessibilitySnapshot?
    ) {
        if (snapshot.packageName != TARGET_PACKAGE) return
        if (now - snapshot.observedAtEpochMillis !in 0..MAX_SNAPSHOT_AGE_MILLIS) return
        if (!actionExecutionPolicy.canClick(adState, snapshot.windowClassName, previous?.windowClassName)) return
        if (snapshot.actionableNodes?.status != ActionableNodeDetectionStatus.DETECTED) return

        val uiState = snapshot.windowClassName to snapshot.structuralNodes
        if (uiState != lastActionableUiState) {
            lastActionableUiState = uiState
            attemptedControlKeys.clear()
        }

        val candidates = snapshot.actionableNodes.candidates
        if (candidates.isEmpty()) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != TARGET_PACKAGE) {
            root.recycle()
            return
        }

        val pending = ArrayDeque<AccessibilityNodeInfo>()
        val liveCandidates = mutableListOf<Pair<AccessibilityNodeInfo, ActionableControlType>>()
        pending.addLast(root)
        try {
            while (pending.isNotEmpty() && liveCandidates.size < MAX_NODES_PER_EVENT) {
                val node = pending.removeFirst()
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val nodeBounds = ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
                val resourceId = node.viewIdResourceName
                val actionType = actionableControlPolicy.classify(resourceId)
                val matchesSnapshot = candidates.any { candidate ->
                    candidate.resourceId == resourceId &&
                        candidate.viewClassName == node.className?.toString() &&
                        candidate.bounds == nodeBounds
                }
                val eligible = matchesSnapshot &&
                    node.isClickable &&
                    node.isEnabled &&
                    node.isVisibleToUser &&
                    nodeBounds.hasArea &&
                    actionType in APPROVED_ACTIONS
                val childCount = node.childCount

                for (index in 0 until childCount) {
                    if (pending.size + liveCandidates.size >= MAX_NODES_PER_EVENT) break
                    node.getChild(index)?.let(pending::addLast)
                }

                if (eligible) {
                    liveCandidates += node to actionType
                } else {
                    node.recycle()
                }
            }

            val selected = liveCandidates
                .filter { (node, type) ->
                    controlKey(node, type) !in attemptedControlKeys
                }
                .minByOrNull { (_, type) -> ACTION_PRIORITY.getValue(type) }

            if (selected != null) {
                val (node, type) = selected
                attemptedControlKeys += controlKey(node, type)
                // One attempt only, even if the platform reports a failed click.
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } finally {
            liveCandidates.forEach { (node, _) -> node.recycle() }
            while (pending.isNotEmpty()) pending.removeFirst().recycle()
        }
    }

    private fun controlKey(node: AccessibilityNodeInfo, type: ActionableControlType): String {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return listOf(
            type.name,
            node.viewIdResourceName.orEmpty(),
            node.className?.toString().orEmpty(),
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom
        ).joinToString("|")
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
                    resourceId = node.viewIdResourceName,
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
        val APPROVED_ACTIONS = setOf(
            ActionableControlType.NEXT_AD,
            ActionableControlType.CLOSE,
            ActionableControlType.CONTINUE
        )
        val ACTION_PRIORITY = mapOf(
            ActionableControlType.NEXT_AD to 0,
            ActionableControlType.CLOSE to 1,
            ActionableControlType.CONTINUE to 2
        )
    }
}
