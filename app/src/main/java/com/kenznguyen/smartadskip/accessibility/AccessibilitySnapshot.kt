package com.kenznguyen.smartadskip.accessibility

data class AccessibilitySnapshot(
    val packageName: String?,
    val eventType: Int,
    val activityClassName: String?,
    val nodeCount: Int,
    val clickableNodeCount: Int
)

class AccessibilitySnapshotPolicy {
    fun create(
        packageName: String?,
        eventType: Int,
        activityClassName: CharSequence?,
        nodeCount: Int,
        clickableNodeCount: Int
    ): AccessibilitySnapshot {
        val safeNodeCount = nodeCount.coerceIn(0, MAX_NODE_COUNT)
        return AccessibilitySnapshot(
            packageName = packageName?.trim()?.takeIf(String::isNotEmpty),
            eventType = eventType,
            activityClassName = activityClassName
                ?.toString()
                ?.trim()
                ?.takeIf(String::isNotEmpty),
            nodeCount = safeNodeCount,
            clickableNodeCount = clickableNodeCount.coerceIn(0, safeNodeCount)
        )
    }

    private companion object {
        const val MAX_NODE_COUNT = 200
    }
}
