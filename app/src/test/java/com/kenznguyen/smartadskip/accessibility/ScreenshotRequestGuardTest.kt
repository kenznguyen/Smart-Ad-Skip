package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotRequestGuardTest {
    @Test
    fun reportsSynchronousExceptionTypeAndMessage() {
        var failure: RuntimeException? = null

        val accepted = ScreenshotRequestGuard.execute(
            request = { throw SecurityException("Screenshot request denied") },
            onFailure = { failure = it }
        )

        assertFalse(accepted)
        assertTrue(failure is SecurityException)
        assertEquals("Screenshot request denied", failure?.message)
    }

    @Test
    fun reportsAcceptedWhenScreenshotRequestIsStarted() {
        var requested = false
        var failure: RuntimeException? = null

        val accepted = ScreenshotRequestGuard.execute(
            request = { requested = true },
            onFailure = { failure = it }
        )

        assertTrue(accepted)
        assertTrue(requested)
        assertEquals(null, failure)
    }

    @Test
    fun containsFailureHandlerExceptions() {
        val accepted = ScreenshotRequestGuard.execute(
            request = { throw IllegalStateException("Service is not ready") },
            onFailure = { throw IllegalArgumentException("diagnostic logger failed") }
        )

        assertFalse(accepted)
    }
}
