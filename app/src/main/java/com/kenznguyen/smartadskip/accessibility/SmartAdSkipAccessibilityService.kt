package com.kenznguyen.smartadskip.accessibility

import android.accessibilityservice.AccessibilityService
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

        val counts = runCatching {
            countVisibleNodes(rootInActiveWindow)
        }.getOrDefault(NodeCounts(0, 0))

        val snapshot = snapshotPolicy.create(
            packageName = event.packageName?.toString(),
            eventType = event.eventType,
            windowClassName = event.className,
            nodeCount = counts.total,
            clickableNodeCount = counts.clickable
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

    private fun countVisibleNodes(root: AccessibilityNodeInfo?): NodeCounts {
        if (root == null) return NodeCounts(0, 0)

        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.addLast(root)
        var total = 0
        var clickable = 0

        while (pending.isNotEmpty() && total < MAX_NODES_PER_EVENT) {
            val node = pending.removeFirst()
            try {
                total += 1
                if (node.isClickable) clickable += 1

                for (index in 0 until node.childCount) {
                    if (total + pending.size >= MAX_NODES_PER_EVENT) break
                    node.getChild(index)?.let(pending::addLast)
                }
            } finally {
                node.recycle()
            }
        }

        while (pending.isNotEmpty()) {
            pending.removeFirst().recycle()
        }

        return NodeCounts(total, clickable)
    }

    private data class NodeCounts(val total: Int, val clickable: Int)

    private companion object {
        const val MAX_NODES_PER_EVENT = 200
    }
}
