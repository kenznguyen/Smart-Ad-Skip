package com.kenznguyen.smartadskip.accessibility

/**
 * Bounded temporal diagnostics for UI structure. This analyzer never classifies ads.
 */
data class TemporalPatternDiagnostics(
    val historyAvailable: Boolean,
    val sampleCount: Int,
    val windowTransitionCount: Int,
    val observedSpanMillis: Long,
    val currentWindowStableMillis: Long
)

class TemporalPatternAnalyzer(
    private val staleAfterMillis: Long = 15_000L,
    private val maxSamples: Int = 10,
    private val targetPackageName: String = "com.funnyvideo.cgame.fruitblast"
) {
    private val history = ArrayDeque<AccessibilitySnapshot>()

    init {
        require(staleAfterMillis > 0L)
        require(maxSamples in 1..10)
    }

    @Synchronized
    fun record(snapshot: AccessibilitySnapshot): TemporalPatternDiagnostics {
        val timestamp = snapshot.observedAtEpochMillis
        val previous = history.lastOrNull()
        val validPackage = snapshot.packageName == targetPackageName
        val validTimestamp = timestamp > 0L &&
            (previous == null || timestamp > previous.observedAtEpochMillis)
        val previousFresh = previous != null &&
            timestamp - previous.observedAtEpochMillis in 0..staleAfterMillis

        if (!validPackage || !validTimestamp) {
            history.clear()
            return unavailable()
        }

        // A valid but delayed sample becomes a new baseline; it is not compared
        // with the stale history preceding it.
        if (previous != null && !previousFresh) {
            history.clear()
        }

        history.addLast(snapshot)
        while (history.size > maxSamples) history.removeFirst()

        val samples = history.toList()
        val transitions = samples.zipWithNext().count { (a, b) ->
            a.windowClassName != b.windowClassName
        }
        val currentWindow = snapshot.windowClassName
        val stableSince = samples.asReversed()
            .takeWhile { it.windowClassName == currentWindow }
            .lastOrNull()?.observedAtEpochMillis ?: timestamp

        return TemporalPatternDiagnostics(
            historyAvailable = true,
            sampleCount = samples.size,
            windowTransitionCount = transitions,
            observedSpanMillis = (timestamp - samples.first().observedAtEpochMillis).coerceAtLeast(0L),
            currentWindowStableMillis = (timestamp - stableSince).coerceAtLeast(0L)
        )
    }

    @Synchronized
    fun reset() {
        history.clear()
    }

    private fun unavailable() = TemporalPatternDiagnostics(
        historyAvailable = false,
        sampleCount = 0,
        windowTransitionCount = 0,
        observedSpanMillis = 0L,
        currentWindowStableMillis = 0L
    )
}
