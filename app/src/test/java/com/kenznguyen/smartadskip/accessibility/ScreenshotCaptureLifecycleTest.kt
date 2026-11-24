package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotCaptureLifecycleTest {
    @Test
    fun allowsOnlyOneScreenshotRequestAtATime() {
        val lifecycle = ScreenshotCaptureLifecycle()

        val first = lifecycle.begin()

        assertNotNull(first)
        assertNull(lifecycle.begin())
        lifecycle.completeCapture(first!!)
        lifecycle.finish(first)
        assertNotNull(lifecycle.begin())
    }

    @Test
    fun resetInvalidatesOutstandingCallbacks() {
        val lifecycle = ScreenshotCaptureLifecycle()
        val oldRequest = lifecycle.begin()!!

        lifecycle.reset()
        val newRequest = lifecycle.begin()!!

        assertFalse(lifecycle.isCurrent(oldRequest))
        assertTrue(lifecycle.isCurrent(newRequest))
    }

    @Test
    fun finishingAnOldRequestDoesNotReleaseANewerRequest() {
        val lifecycle = ScreenshotCaptureLifecycle()
        val oldRequest = lifecycle.begin()!!

        lifecycle.reset()
        val newRequest = lifecycle.begin()!!
        lifecycle.finish(oldRequest)

        assertTrue(lifecycle.isCurrent(newRequest))
        assertNull(lifecycle.begin())
    }

    @Test
    fun finishReleasesOnlyTheCurrentRequest() {
        val lifecycle = ScreenshotCaptureLifecycle()
        val request = lifecycle.begin()!!

        lifecycle.finish(request)

        assertFalse(lifecycle.isCurrent(request))
        assertNotNull(lifecycle.begin())
    }

    @Test
    fun captureTimeoutReleasesOnlyThePendingRequest() {
        val lifecycle = ScreenshotCaptureLifecycle()
        val request = lifecycle.begin()!!

        assertTrue(lifecycle.timeoutCapture(request))
        assertFalse(lifecycle.isCurrent(request))
        assertNotNull(lifecycle.begin())
    }

    @Test
    fun lateSuccessCannotCompleteTimedOutCapture() {
        val lifecycle = ScreenshotCaptureLifecycle()
        val request = lifecycle.begin()!!

        assertTrue(lifecycle.timeoutCapture(request))

        assertFalse(lifecycle.completeCapture(request))
        assertFalse(lifecycle.isCurrent(request))
    }

    @Test
    fun captureCallbackCanCompleteOnlyOnce() {
        val lifecycle = ScreenshotCaptureLifecycle()
        val request = lifecycle.begin()!!

        assertTrue(lifecycle.completeCapture(request))
        assertFalse(lifecycle.completeCapture(request))
        assertFalse(lifecycle.timeoutCapture(request))
        assertTrue(lifecycle.isCurrent(request))
    }

    @Test
    fun timeoutFromOldGenerationCannotReleaseNewRequest() {
        val lifecycle = ScreenshotCaptureLifecycle()
        val oldRequest = lifecycle.begin()!!

        lifecycle.reset()
        val newRequest = lifecycle.begin()!!

        assertFalse(lifecycle.timeoutCapture(oldRequest))
        assertTrue(lifecycle.isCurrent(newRequest))
    }
}
