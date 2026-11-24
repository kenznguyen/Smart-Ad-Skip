package com.kenznguyen.smartadskip.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeIpcTimeoutPolicyTest {
    @Test fun connectionWaitHasFiniteBudget() {
        assertEquals(10_000L, NativeIpcTimeoutPolicy.CONNECTION_TIMEOUT_MILLIS)
    }

    @Test fun helloUsesTransportTimeoutAndDoesNotKillNativeProcess() {
        assertEquals(
            5_000L,
            NativeIpcTimeoutPolicy.timeoutMillis(NativeIpcTimeoutPolicy.Request.HELLO, 30_000L)
        )
        assertFalse(
            NativeIpcTimeoutPolicy.terminateProcessOnTimeout(NativeIpcTimeoutPolicy.Request.HELLO)
        )
    }

    @Test fun inferenceTimeoutMatchesConfiguredNativeDeadlineAndQuarantinesProcess() {
        assertEquals(
            30_000L,
            NativeIpcTimeoutPolicy.timeoutMillis(NativeIpcTimeoutPolicy.Request.INFER, 30_000L)
        )
        assertTrue(
            NativeIpcTimeoutPolicy.terminateProcessOnTimeout(NativeIpcTimeoutPolicy.Request.INFER)
        )
    }

    @Test fun loadGetsBoundedStartupBudgetAndProcessIsolationOnTimeout() {
        assertEquals(
            150_000L,
            NativeIpcTimeoutPolicy.timeoutMillis(NativeIpcTimeoutPolicy.Request.LOAD, 30_000L)
        )
        assertTrue(
            NativeIpcTimeoutPolicy.terminateProcessOnTimeout(NativeIpcTimeoutPolicy.Request.LOAD)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveNativeTimeout() {
        NativeIpcTimeoutPolicy.timeoutMillis(NativeIpcTimeoutPolicy.Request.INFER, 0L)
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsOverflowingLoadTimeout() {
        NativeIpcTimeoutPolicy.timeoutMillis(NativeIpcTimeoutPolicy.Request.LOAD, Long.MAX_VALUE)
    }
}
