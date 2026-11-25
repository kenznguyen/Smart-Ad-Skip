package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMonitoringPolicyTest {
    @Test fun schedulesBoundedRechecksOnlyForKnownForegroundAd() {
        val policy = AdMonitoringPolicy(maxCycles = 3, initialDelayMillis = 1_000L, maxDelayMillis = 4_000L)

        assertEquals(1_000L, policy.nextDelayMillis(isTargetForeground = true, isKnownAdState = true))
        assertEquals(2_000L, policy.nextDelayMillis(isTargetForeground = true, isKnownAdState = true))
        assertEquals(4_000L, policy.nextDelayMillis(isTargetForeground = true, isKnownAdState = true))
        assertNull(policy.nextDelayMillis(isTargetForeground = true, isKnownAdState = true))
    }

    @Test fun doesNotScheduleForUnknownAdOrNonTargetForeground() {
        val policy = AdMonitoringPolicy()

        assertNull(policy.nextDelayMillis(isTargetForeground = false, isKnownAdState = true))
        assertNull(policy.nextDelayMillis(isTargetForeground = true, isKnownAdState = false))
        assertEquals(1_500L, policy.nextDelayMillis(isTargetForeground = true, isKnownAdState = true))
    }

    @Test fun capsBackoffAndResetStartsANewMonitoringSession() {
        val policy = AdMonitoringPolicy(maxCycles = 8, initialDelayMillis = 1_000L, maxDelayMillis = 3_000L)

        assertEquals(1_000L, policy.nextDelayMillis(true, true))
        assertEquals(2_000L, policy.nextDelayMillis(true, true))
        assertEquals(3_000L, policy.nextDelayMillis(true, true))
        assertEquals(3_000L, policy.nextDelayMillis(true, true))
        policy.reset()
        assertEquals(1_000L, policy.nextDelayMillis(true, true))
    }

    @Test fun exposesWhetherMonitoringHasPendingBudget() {
        val policy = AdMonitoringPolicy(maxCycles = 1)

        assertTrue(policy.hasRemainingCycles)
        policy.nextDelayMillis(true, true)
        assertFalse(policy.hasRemainingCycles)
    }
}
