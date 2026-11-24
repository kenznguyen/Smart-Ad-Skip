package com.kenznguyen.smartadskip.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

class NativeRuntimeLifecycleTest {
    @Test fun timeoutRetainsNativeOwnershipUntilJniActuallyReturnsThenRecovers() {
        val executor = Executors.newSingleThreadExecutor()
        val clock = AtomicLong(1_000_000_000L)
        val lifecycle = NativeRuntimeLifecycle(executor) { clock.get() }
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val first = lifecycle.infer {
                started.countDown()
                check(release.await(2, TimeUnit.SECONDS))
                "first"
            }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            lifecycle.markInferenceCallerTimedOut()
            assertEquals(NativeRuntimeLifecycle.InferenceState.CALLER_TIMED_OUT, lifecycle.inferenceSnapshot().state)
            assertTrue(runCatching { lifecycle.infer { "overlap" } }.exceptionOrNull() is NativeInferenceBusyException)
            release.countDown()
            assertEquals("first", first.get(2, TimeUnit.SECONDS))
            assertEquals(NativeRuntimeLifecycle.InferenceState.IDLE, lifecycle.inferenceSnapshot().state)
            assertEquals("recovered", lifecycle.infer { "recovered" }.get(2, TimeUnit.SECONDS))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun agedInferenceIsDiagnosedAsStuckButNeverHasOwnershipStolen() {
        val executor = Executors.newSingleThreadExecutor()
        val clock = AtomicLong(0L)
        val lifecycle = NativeRuntimeLifecycle(executor) { clock.get() }
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            lifecycle.infer { started.countDown(); check(release.await(2, TimeUnit.SECONDS)) }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            clock.set(TimeUnit.SECONDS.toNanos(31))
            assertEquals(NativeRuntimeLifecycle.InferenceState.STUCK, lifecycle.inferenceSnapshot().state)
            val busy = runCatching { lifecycle.infer { "overlap" } }.exceptionOrNull()
            assertTrue(busy is NativeInferenceBusyException)
            assertTrue((busy as NativeInferenceBusyException).diagnostic.contains("state=stuck"))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun concurrentInferenceIsRejectedAndSlotRecoversOnSuccess() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val first = lifecycle.infer { started.countDown(); check(release.await(2, TimeUnit.SECONDS)); "first" }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertTrue(runCatching { lifecycle.infer { "duplicate" } }.exceptionOrNull() is NativeInferenceBusyException)
            release.countDown()
            assertEquals("first", first.get(2, TimeUnit.SECONDS))
            assertEquals("second", lifecycle.infer { "second" }.get(2, TimeUnit.SECONDS))
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test fun inferenceOwnershipIsReleasedAfterNativeException() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            assertTrue(runCatching { lifecycle.infer<String> { error("native failure") }.get(2, TimeUnit.SECONDS) }.isFailure)
            assertEquals(NativeRuntimeLifecycle.InferenceState.IDLE, lifecycle.inferenceSnapshot().state)
            assertEquals("ok", lifecycle.infer { "ok" }.get(2, TimeUnit.SECONDS))
        } finally { executor.shutdownNow() }
    }

    @Test fun inferenceOwnershipIsReleasedAfterCancellationOfNativeTask() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            assertTrue(runCatching {
                lifecycle.infer<String> { throw java.util.concurrent.CancellationException("cancelled") }.get(2, TimeUnit.SECONDS)
            }.isFailure)
            assertEquals(NativeRuntimeLifecycle.InferenceState.IDLE, lifecycle.inferenceSnapshot().state)
            assertEquals("recovered", lifecycle.infer { "recovered" }.get(2, TimeUnit.SECONDS))
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
            val inference = lifecycle.infer { started.countDown(); check(release.await(2, TimeUnit.SECONDS)); "late" }
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
