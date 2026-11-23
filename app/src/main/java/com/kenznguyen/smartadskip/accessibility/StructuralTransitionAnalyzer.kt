package com.kenznguyen.smartadskip.accessibility

/**
 * Describes structural changes only. These values are diagnostic and are never ad evidence.
 */
data class StructuralTransitionDiagnostics(
    val comparisonAvailable: Boolean,
    val windowClassChanged: Boolean? = null,
    val structuralNodeDelta: Int? = null,
    val clickableNodeDelta: Int? = null
)

class StructuralTransitionAnalyzer(
    private val staleAfterMillis: Long = 15_000L,
    private val targetPackageName: String = "com.funnyvideo.cgame.fruitblast"
) {
    fun analyze(
        previous: AccessibilitySnapshot?,
        current: AccessibilitySnapshot,
        nowEpochMillis: Long
    ): StructuralTransitionDiagnostics {
        if (previous == null || current.packageName != targetPackageName ||
            previous.packageName != current.packageName ||
            !isFresh(previous.observedAtEpochMillis, nowEpochMillis) ||
            !isFresh(current.observedAtEpochMillis, nowEpochMillis) ||
            previous.observedAtEpochMillis >= current.observedAtEpochMillis
        ) {
            return StructuralTransitionDiagnostics(comparisonAvailable = false)
        }

        return StructuralTransitionDiagnostics(
            comparisonAvailable = true,
            windowClassChanged = previous.windowClassName != current.windowClassName,
            structuralNodeDelta = current.nodeCount - previous.nodeCount,
            clickableNodeDelta = current.clickableNodeCount - previous.clickableNodeCount
        )
    }

    private fun isFresh(timestamp: Long, now: Long): Boolean {
        val age = now - timestamp
        return timestamp > 0L && age in 0..staleAfterMillis
    }
}
