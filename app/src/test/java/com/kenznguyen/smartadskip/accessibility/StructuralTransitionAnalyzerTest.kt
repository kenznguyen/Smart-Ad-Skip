package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuralTransitionAnalyzerTest {
    private val analyzer = StructuralTransitionAnalyzer()
    private val now = 1_800_000_000_000L

    @Test
    fun reportsWindowAndNodeCountChangesForFreshSnapshotsInSamePackage() {
        val previous = snapshot(window = "android.webkit.WebView", nodes = 49, clickable = 1, at = now - 1_000)
        val current = snapshot(window = "android.view.View", nodes = 22, clickable = 1, at = now)

        val result = analyzer.analyze(previous, current, now)

        assertTrue(result.comparisonAvailable)
        assertTrue(result.windowClassChanged!!)
        assertEquals(-27, result.structuralNodeDelta)
        assertEquals(0, result.clickableNodeDelta)
    }

    @Test
    fun reportsNoWindowChangeWhenWindowClassIsUnchanged() {
        val previous = snapshot(window = "android.view.View", nodes = 22, clickable = 1, at = now - 1_000)
        val current = snapshot(window = "android.view.View", nodes = 6, clickable = 0, at = now)

        val result = analyzer.analyze(previous, current, now)

        assertEquals(false, result.windowClassChanged)
        assertEquals(-16, result.structuralNodeDelta)
        assertEquals(-1, result.clickableNodeDelta)
    }

    @Test
    fun doesNotCompareSnapshotsFromDifferentPackages() {
        val previous = snapshot(packageName = "com.other.app", at = now - 1_000)
        val current = snapshot(at = now)

        assertFalse(analyzer.analyze(previous, current, now).comparisonAvailable)
    }

    @Test
    fun doesNotCompareStaleSnapshots() {
        val previous = snapshot(at = now - 16_000)
        val current = snapshot(at = now)

        assertFalse(analyzer.analyze(previous, current, now).comparisonAvailable)
    }

    @Test
    fun doesNotCompareWhenTimestampDoesNotAdvance() {
        val previous = snapshot(at = now)
        val current = snapshot(at = now)

        assertFalse(analyzer.analyze(previous, current, now).comparisonAvailable)
    }

    @Test
    fun missingPreviousSnapshotProducesUnavailableBaseline() {
        val result = analyzer.analyze(null, snapshot(at = now), now)

        assertFalse(result.comparisonAvailable)
        assertEquals(null, result.structuralNodeDelta)
    }

    private fun snapshot(
        packageName: String = "com.funnyvideo.cgame.fruitblast",
        window: String = "android.widget.FrameLayout",
        nodes: Int = 10,
        clickable: Int = 1,
        at: Long
    ) = AccessibilitySnapshot(
        packageName = packageName,
        eventType = 32,
        windowClassName = window,
        nodeCount = nodes,
        clickableNodeCount = clickable,
        observedAtEpochMillis = at
    )
}
