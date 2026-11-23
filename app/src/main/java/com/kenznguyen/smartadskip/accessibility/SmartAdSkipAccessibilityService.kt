package com.kenznguyen.smartadskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

class SmartAdSkipAccessibilityService : AccessibilityService() {
    private val snapshotPolicy = AccessibilitySnapshotPolicy()

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityMonitorRepository.setConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val nodes = runCatching { collectStructuralNodes(rootInActiveWindow) }
            .getOrDefault(emptyList())
        val snapshot = snapshotPolicy.create(
            packageName = event.packageName?.toString(),
            eventType = event.eventType,
            windowClassName = event.className,
            nodeCount = nodes.size,
            clickableNodeCount = nodes.count { it.clickable },
            observedAtEpochMillis = System.currentTimeMillis(),
            structuralNodes = nodes
        )
        AccessibilityMonitorRepository.publish(snapshot)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AccessibilityMonitorRepository.setConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityMonitorRepository.setConnected(false)
        super.onDestroy()
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
