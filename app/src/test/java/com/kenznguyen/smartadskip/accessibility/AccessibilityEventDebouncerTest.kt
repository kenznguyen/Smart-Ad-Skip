package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityEventDebouncerTest {
    @Test
    fun coalescesRapidOrdinaryEventsToLatestObservation() {
        val debouncer = AccessibilityEventDebouncer<String>(120L)

        assertEquals(AccessibilityEventDebouncer.Submission.Deferred(120L),
            debouncer.submit("content-1", isCriticalTransition = false))
        assertEquals(AccessibilityEventDebouncer.Submission.Deferred(120L),
            debouncer.submit("content-2", isCriticalTransition = false))

        assertEquals("content-2", debouncer.flush())
        assertNull(debouncer.flush())
    }

    @Test
    fun criticalActivityTransitionBypassesDebounceAndClearsStalePendingEvent() {
        val debouncer = AccessibilityEventDebouncer<String>(120L)
        debouncer.submit("old-content", isCriticalTransition = false)

        val transition = debouncer.submit("window-state-changed", isCriticalTransition = true)

        assertEquals(AccessibilityEventDebouncer.Submission.Immediate("window-state-changed"), transition)
        assertNull(debouncer.flush())
    }

    @Test
    fun everyCriticalTransitionIsDeliveredImmediately() {
        val debouncer = AccessibilityEventDebouncer<String>(120L)

        val first = debouncer.submit("activity-a", isCriticalTransition = true)
        val second = debouncer.submit("activity-b", isCriticalTransition = true)

        assertEquals(AccessibilityEventDebouncer.Submission.Immediate("activity-a"), first)
        assertEquals(AccessibilityEventDebouncer.Submission.Immediate("activity-b"), second)
    }

    @Test
    fun clearDiscardsDeferredEventDuringServiceLifecycleReset() {
        val debouncer = AccessibilityEventDebouncer<String>(120L)
        debouncer.submit("pending", isCriticalTransition = false)

        debouncer.clear()

        assertNull(debouncer.flush())
    }
}
