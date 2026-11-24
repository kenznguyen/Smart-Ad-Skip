package com.kenznguyen.smartadskip.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

class NativeRuntimeLifecycleTest {
    @Test fun timeoutRetainsOwnershipUntilLateNativeReturnThenRecovers() {
        val executor = Executors.newSingleThreadExecutor()
        val clock = AtomicLong(1_000_000_000L)
        val events = mutableListOf<String>()
        val lifecycle = NativeRuntimeLifecycle(executor, { clock.get() }, { synchronized(events) { events += it } })
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val first = lifecycle.infer(requestId = 42L) {
                started.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                "first"
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            lifecycle.markInferenceCallerTimedOut()
            assertEquals(NativeRuntimeLifecycle.InferenceState.CALLER_TIMED_OUT, lifecycle.inferenceSnapshot().state)
            assertTrue(runCatching { lifecycle.infer(requestId = 43L) { "overlap" } }.exceptionOrNull() is NativeInferenceBusyException)
            release.countDown()
            assertEquals("first", first.get(2, TimeUnit.SECONDS))
            assertEquals(NativeRuntimeLifecycle.InferenceState.IDLE, lifecycle.inferenceSnapshot().state)
            assertEquals("recovered", lifecycle.infer(requestId = 44L) { "recovered" }.get(2, TimeUnit.SECONDS))
            val log = synchronized(events) { events.joinToString("\n") }
            assertTrue(log.contains("inference_started request_id=42"))
            assertTrue(log.contains("inference_caller_timed_out request_id=42"))
            assertTrue(log.contains("inference_native_returned request_id=42"))
            assertTrue(log.contains("inference_ownership_released request_id=42"))
            assertTrue(log.contains("thread_id="))
            assertTrue(log.contains("native_elapsed_ms="))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun actualNativeDurationTransitionsCallerTimedOutToStuckWithoutStealingOwnership() {
        val executor = Executors.newSingleThreadExecutor()
        val clock = AtomicLong(0L)
        val lifecycle = NativeRuntimeLifecycle(executor, { clock.get() })
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            lifecycle.infer(requestId = 7L) {
                started.countDown()
                check(release.await(2, TimeUnit.SECONDS))
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            lifecycle.markInferenceCallerTimedOut()
            clock.set(TimeUnit.SECONDS.toNanos(29))
            assertEquals(NativeRuntimeLifecycle.InferenceState.CALLER_TIMED_OUT, lifecycle.inferenceSnapshot().state)
            clock.set(TimeUnit.SECONDS.toNanos(31))
            assertEquals(NativeRuntimeLifecycle.InferenceState.STUCK, lifecycle.inferenceSnapshot().state)
            val busy = runCatching { lifecycle.infer(requestId = 8L) { "overlap" } }.exceptionOrNull()
            assertTrue(busy is NativeInferenceBusyException)
            assertTrue((busy as NativeInferenceBusyException).diagnostic.contains("state=stuck"))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun nativeCallCanReturnAfterStuckClassificationAndReleaseForRecovery() {
        val executor = Executors.newSingleThreadExecutor()
        val clock = AtomicLong(0L)
        val lifecycle = NativeRuntimeLifecycle(executor, { clock.get() })
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val late = lifecycle.infer(requestId = 9L) {
                started.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                "late"
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            clock.set(TimeUnit.SECONDS.toNanos(31))
            assertEquals(NativeRuntimeLifecycle.InferenceState.STUCK, lifecycle.inferenceSnapshot().state)
            assertTrue(runCatching { lifecycle.infer(requestId = 10L) { "unsafe" } }.exceptionOrNull() is NativeInferenceBusyException)
            release.countDown()
            assertEquals("late", late.get(2, TimeUnit.SECONDS))
            assertEquals(NativeRuntimeLifecycle.InferenceState.IDLE, lifecycle.inferenceSnapshot().state)
            assertEquals("recovered", lifecycle.infer(requestId = 11L) { "recovered" }.get(2, TimeUnit.SECONDS))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun duplicateInferenceIsRejectedAndSlotRecoversAfterSuccess() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val first = lifecycle.infer(requestId = 1L) { started.countDown(); check(release.await(2, TimeUnit.SECONDS)); "first" }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertTrue(runCatching { lifecycle.infer(requestId = 2L) { "duplicate" } }.exceptionOrNull() is NativeInferenceBusyException)
            release.countDown()
            assertEquals("first", first.get(2, TimeUnit.SECONDS))
            assertEquals("second", lifecycle.infer(requestId = 3L) { "second" }.get(2, TimeUnit.SECONDS))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun inferenceOwnershipIsReleasedAfterNativeException() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            assertTrue(runCatching { lifecycle.infer<String>(requestId = 1L) { error("native failure") }.get(2, TimeUnit.SECONDS) }.isFailure)
            assertEquals(NativeRuntimeLifecycle.InferenceState.IDLE, lifecycle.inferenceSnapshot().state)
            assertEquals("ok", lifecycle.infer(requestId = 2L) { "ok" }.get(2, TimeUnit.SECONDS))
        } finally { executor.shutdownNow() }
    }

    @Test fun inferenceOwnershipIsReleasedAfterNativeCancellationException() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            assertTrue(runCatching {
                lifecycle.infer<String>(requestId = 1L) { throw java.util.concurrent.CancellationException("cancelled") }.get(2, TimeUnit.SECONDS)
            }.isFailure)
            assertEquals(NativeRuntimeLifecycle.InferenceState.IDLE, lifecycle.inferenceSnapshot().state)
            assertEquals("recovered", lifecycle.infer(requestId = 2L) { "recovered" }.get(2, TimeUnit.SECONDS))
        } finally { executor.shutdownNow() }
    }

    @Test fun closeIsQueuedBehindNativeInference() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val inference = lifecycle.infer(requestId = 1L) { started.countDown(); check(release.await(2, TimeUnit.SECONDS)); "late" }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val close = lifecycle.close { closed.countDown() }
            assertFalse(closed.await(50, TimeUnit.MILLISECONDS))
            release.countDown()
            close.get(2, TimeUnit.SECONDS)
            assertTrue(closed.await(1, TimeUnit.SECONDS))
            assertTrue(inference.isCompletedExceptionally)
            assertEquals(NativeRuntimeLifecycle.State.CLOSED, lifecycle.state)
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun failedLoadCannotTransitionToReady() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        try {
            assertTrue(runCatching { lifecycle.load { error("load failed") }.get(2, TimeUnit.SECONDS) }.isFailure)
            assertEquals(NativeRuntimeLifecycle.State.FAILED, lifecycle.state)
        } finally { executor.shutdownNow() }
    }
}
