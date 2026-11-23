package com.kenznguyen.smartadskip.accessibility

data class AccessibilitySnapshot(
    val packageName: String?,
    val eventType: Int,
    val windowClassName: String?,
    val nodeCount: Int,
    val clickableNodeCount: Int,
    val observedAtEpochMillis: Long
)

class AccessibilitySnapshotPolicy {
    fun create(
        packageName: String?,
        eventType: Int,
        windowClassName: CharSequence?,
        nodeCount: Int,
        clickableNodeCount: Int,
        observedAtEpochMillis: Long = System.currentTimeMillis()
    ): AccessibilitySnapshot {
        val safeNodeCount = nodeCount.coerceIn(0, MAX_NODE_COUNT)
        return AccessibilitySnapshot(
            packageName = packageName?.trim()?.takeIf(String::isNotEmpty),
            eventType = eventType,
            windowClassName = windowClassName?.toString()?.trim()?.takeIf(String::isNotEmpty),
            nodeCount = safeNodeCount,
            clickableNodeCount = clickableNodeCount.coerceIn(0, safeNodeCount),
            observedAtEpochMillis = observedAtEpochMillis.coerceAtLeast(0L)
        )
    }

    private companion object {
        const val MAX_NODE_COUNT = 200
    }
}
