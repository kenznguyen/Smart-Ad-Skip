package com.kenznguyen.smartadskip.ai

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeRuntimeLifecycleTest {
    @Test fun closeIsQueuedAfterRunningInferenceAndNeverBlocksCaller() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        val inferenceStarted = CountDownLatch(1)
        val releaseInference = CountDownLatch(1)
        val closeRan = CountDownLatch(1)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val inference = lifecycle.infer {
                inferenceStarted.countDown()
                check(releaseInference.await(2, TimeUnit.SECONDS))
                "late-result"
            }
            assertTrue(inferenceStarted.await(2, TimeUnit.SECONDS))

            val caller = Thread.currentThread()
            val close = lifecycle.close { 
                assertFalse(Thread.currentThread() === caller)
                closeRan.countDown()
            }
            assertFalse(closeRan.await(50, TimeUnit.MILLISECONDS))
            releaseInference.countDown()
            close.get(2, TimeUnit.SECONDS)
            assertTrue(closeRan.await(1, TimeUnit.SECONDS))
            assertTrue(inference.isCompletedExceptionally)
            assertEquals(NativeRuntimeLifecycle.State.CLOSED, lifecycle.state)
        } finally {
            releaseInference.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun inferenceIsRejectedAfterCloseIsRequested() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            lifecycle.close { }.get(2, TimeUnit.SECONDS)
            val rejected = runCatching { lifecycle.infer { "must-not-run" } }.exceptionOrNull()
            assertTrue(rejected is IllegalStateException)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun failedLoadCannotTransitionToReady() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        try {
            val failure = runCatching {
                lifecycle.load { error("native_load_failed") }.get(2, TimeUnit.SECONDS)
            }.exceptionOrNull()
            assertTrue(failure != null)
            assertEquals(NativeRuntimeLifecycle.State.FAILED, lifecycle.state)
        } finally {
            executor.shutdownNow()
        }
    }
}
