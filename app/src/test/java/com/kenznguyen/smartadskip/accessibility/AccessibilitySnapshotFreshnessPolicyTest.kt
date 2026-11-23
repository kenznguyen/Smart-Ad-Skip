package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySnapshotFreshnessPolicyTest {
    @Test
    fun acceptsSnapshotWithinFiveSecondWindow() {
        assertTrue(AccessibilitySnapshotFreshnessPolicy.isFresh(10_000L, 15_000L))
    }

    @Test
    fun rejectsSnapshotOlderThanFiveSeconds() {
        assertFalse(AccessibilitySnapshotFreshnessPolicy.isFresh(9_999L, 15_000L))
    }

    @Test
    fun acceptsSnapshotAtExactFiveSecondBoundary() {
        assertTrue(AccessibilitySnapshotFreshnessPolicy.isFresh(10_000L, 15_000L))
    }

    @Test
    fun rejectsFutureDatedSnapshot() {
        assertFalse(AccessibilitySnapshotFreshnessPolicy.isFresh(15_001L, 15_000L))
    }
}
