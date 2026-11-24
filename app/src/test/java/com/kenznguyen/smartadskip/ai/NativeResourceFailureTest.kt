package com.kenznguyen.smartadskip.ai

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeResourceFailureTest {
    @Test fun failedInferenceDoesNotPreventNativeClose() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        val closeCount = AtomicInteger()
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val failure = runCatching {
                lifecycle.infer<String>(requestId = 1L) { throw IllegalStateException("injected_native_inference_failure") }
                    .get(2, TimeUnit.SECONDS)
            }.exceptionOrNull()

            assertTrue(failure is ExecutionException)
            assertEquals("injected_native_inference_failure", failure?.cause?.message)
            lifecycle.close { closeCount.incrementAndGet() }.get(2, TimeUnit.SECONDS)
            assertEquals(1, closeCount.get())
            assertEquals(NativeRuntimeLifecycle.State.CLOSED, lifecycle.state)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun repeatedCloseSharesOneCleanupOperation() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        val closeCount = AtomicInteger()
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val first = lifecycle.close { closeCount.incrementAndGet() }
            val second = lifecycle.close { closeCount.incrementAndGet() }

            assertSame(first, second)
            first.get(2, TimeUnit.SECONDS)
            assertEquals(1, closeCount.get())
            assertEquals(NativeRuntimeLifecycle.State.CLOSED, lifecycle.state)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun failedNativeCloseStillTransitionsLifecycleToClosed() {
        val executor = Executors.newSingleThreadExecutor()
        val lifecycle = NativeRuntimeLifecycle(executor)
        try {
            lifecycle.load { }.get(2, TimeUnit.SECONDS)
            val failure = runCatching {
                lifecycle.close { throw IllegalStateException("injected_native_close_failure") }
                    .get(2, TimeUnit.SECONDS)
            }.exceptionOrNull()

            assertTrue(failure is ExecutionException)
            assertEquals("injected_native_close_failure", failure?.cause?.message)
            assertEquals(NativeRuntimeLifecycle.State.CLOSED, lifecycle.state)
        } finally {
            executor.shutdownNow()
        }
    }
}
