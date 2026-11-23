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
        lifecycle.finish(first!!)
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
}
