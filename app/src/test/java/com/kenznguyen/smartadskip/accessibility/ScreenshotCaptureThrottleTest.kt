package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotCaptureThrottleTest {
    @Test fun firstCaptureIsAllowedImmediately() {
        val throttle = ScreenshotCaptureThrottle(minIntervalMillis = 1_000L)
        assertTrue(throttle.tryAcquire(nowMillis = 10_000L))
    }

    @Test fun requestsInsideMinimumIntervalAreRejected() {
        val throttle = ScreenshotCaptureThrottle(minIntervalMillis = 1_000L)
        assertTrue(throttle.tryAcquire(nowMillis = 10_000L))
        assertFalse(throttle.tryAcquire(nowMillis = 10_999L))
    }

    @Test fun requestAtExactMinimumIntervalIsAllowed() {
        val throttle = ScreenshotCaptureThrottle(minIntervalMillis = 1_000L)
        assertTrue(throttle.tryAcquire(nowMillis = 10_000L))
        assertTrue(throttle.tryAcquire(nowMillis = 11_000L))
    }

    @Test fun rejectedRequestsDoNotMoveTheThrottleWindow() {
        val throttle = ScreenshotCaptureThrottle(minIntervalMillis = 1_000L)
        assertTrue(throttle.tryAcquire(nowMillis = 10_000L))
        assertFalse(throttle.tryAcquire(nowMillis = 10_900L))
        assertTrue(throttle.tryAcquire(nowMillis = 11_000L))
    }

    @Test fun reportsRemainingTimeUntilNextCaptureIsAllowed() {
        val throttle = ScreenshotCaptureThrottle(minIntervalMillis = 1_000L)
        assertTrue(throttle.tryAcquire(nowMillis = 10_000L))

        assertEquals(500L, throttle.millisUntilAllowed(nowMillis = 10_500L))
        assertEquals(0L, throttle.millisUntilAllowed(nowMillis = 11_000L))
    }

    @Test fun resetAllowsAnImmediateNewCapture() {
        val throttle = ScreenshotCaptureThrottle(minIntervalMillis = 1_000L)
        assertTrue(throttle.tryAcquire(nowMillis = 10_000L))
        throttle.reset()
        assertTrue(throttle.tryAcquire(nowMillis = 10_100L))
    }

    @Test fun synchronousRequestFailureCanReleaseItsReservation() {
        val throttle = ScreenshotCaptureThrottle(minIntervalMillis = 1_000L)
        assertTrue(throttle.tryAcquire(nowMillis = 10_000L))
        throttle.release(requestAtMillis = 10_000L)
        assertTrue(throttle.tryAcquire(nowMillis = 10_100L))
    }

    @Test fun staleFailureCannotReleaseANewerReservation() {
        val throttle = ScreenshotCaptureThrottle(minIntervalMillis = 1_000L)
        assertTrue(throttle.tryAcquire(nowMillis = 10_000L))
        assertTrue(throttle.tryAcquire(nowMillis = 11_000L))
        throttle.release(requestAtMillis = 10_000L)
        assertFalse(throttle.tryAcquire(nowMillis = 11_100L))
    }
}
