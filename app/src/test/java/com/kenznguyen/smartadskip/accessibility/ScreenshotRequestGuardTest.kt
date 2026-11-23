package com.kenznguyen.smartadskip.accessibility

import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotRequestGuardTest {
    @Test
    fun resetsInFlightStateWhenScreenshotRequestThrowsSynchronously() {
        val inFlight = AtomicBoolean(true)
        var failureHandled = false

        val accepted = ScreenshotRequestGuard.execute(
            request = { throw SecurityException("Screenshot request denied") },
            onFailure = {
                inFlight.set(false)
                failureHandled = true
            }
        )

        assertFalse(accepted)
        assertFalse(inFlight.get())
        assertTrue(failureHandled)
    }

    @Test
    fun reportsAcceptedWhenScreenshotRequestIsStarted() {
        var requested = false
        var failureHandled = false

        val accepted = ScreenshotRequestGuard.execute(
            request = { requested = true },
            onFailure = { failureHandled = true }
        )

        assertTrue(accepted)
        assertTrue(requested)
        assertFalse(failureHandled)
    }

    @Test
    fun handlesOtherSynchronousRuntimeFailuresWithoutPropagating() {
        var failureHandled = false

        val accepted = ScreenshotRequestGuard.execute(
            request = { throw IllegalStateException("Service is not ready") },
            onFailure = { failureHandled = true }
        )

        assertFalse(accepted)
        assertTrue(failureHandled)
    }
}
