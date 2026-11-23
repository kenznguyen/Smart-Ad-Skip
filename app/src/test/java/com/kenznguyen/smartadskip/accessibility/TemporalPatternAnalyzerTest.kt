package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalPatternAnalyzerTest {
    private val analyzer = TemporalPatternAnalyzer()
    private val start = 1_800_000_000_000L

    @Test
    fun tracksWindowStabilityAcrossFreshSamePackageSnapshots() {
        analyzer.record(snapshot("A", start))
        analyzer.record(snapshot("A", start + 1_000))
        val result = analyzer.record(snapshot("A", start + 3_000))

        assertTrue(result.historyAvailable)
        assertEquals(3, result.sampleCount)
        assertEquals(0, result.windowTransitionCount)
        assertEquals(3_000L, result.currentWindowStableMillis)
        assertEquals(3_000L, result.observedSpanMillis)
    }

    @Test
    fun countsWindowClassTransitionsWithoutInferringAdState() {
        analyzer.record(snapshot("A", start))
        analyzer.record(snapshot("B", start + 1_000))
        val result = analyzer.record(snapshot("A", start + 2_000))

        assertEquals(2, result.windowTransitionCount)
        assertEquals(0L, result.currentWindowStableMillis)
        assertEquals(2_000L, result.observedSpanMillis)
    }

    @Test
    fun resetsHistoryForStaleOrNonAdvancingSnapshots() {
        analyzer.record(snapshot("A", start))
        val result = analyzer.record(snapshot("B", start + 20_000))

        assertTrue(result.historyAvailable)
        assertEquals(1, result.sampleCount)
        assertEquals(0, result.windowTransitionCount)
        assertEquals(0L, result.observedSpanMillis)
    }

    @Test
    fun rejectsOtherPackagesAndKeepsNoHistory() {
        val result = analyzer.record(snapshot("A", start, packageName = "other.app"))

        assertFalse(result.historyAvailable)
        assertEquals(0, result.sampleCount)
    }

    private fun snapshot(window: String, at: Long, packageName: String = "com.funnyvideo.cgame.fruitblast") =
        AccessibilitySnapshot(
            packageName = packageName,
            eventType = 32,
            windowClassName = window,
            nodeCount = 10,
            clickableNodeCount = 1,
            observedAtEpochMillis = at
        )
}
