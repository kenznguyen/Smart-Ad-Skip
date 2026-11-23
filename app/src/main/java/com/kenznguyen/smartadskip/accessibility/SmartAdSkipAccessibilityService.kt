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
    private val closeButtonDetector = CloseButtonDetector()
    private var previousSnapshot: AccessibilitySnapshot? = null

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
            temporalPattern = temporalPatternAnalyzer.record(baseSnapshot)
        )
        val state = adStateClassifier.classify(snapshot, now, previousSnapshot)
        val enrichedSnapshot = snapshot.copy(
            closeButton = closeButtonDetector.detect(snapshot, previousSnapshot, state, now)
        )
        AdDetectionRepository.publish(state)
        previousSnapshot = enrichedSnapshot
        AccessibilityMonitorRepository.publish(enrichedSnapshot)
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
        temporalPatternAnalyzer.reset()
        AdDetectionRepository.reset()
        AccessibilityMonitorRepository.setConnected(false)
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
                    bounds = ScreenBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
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
    }
}
