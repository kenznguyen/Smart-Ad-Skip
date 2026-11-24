package com.kenznguyen.smartadskip.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeProcessSessionPolicyTest {
    @Test fun rejectsCallbacksFromOlderBindingGeneration() {
        assertFalse(NativeProcessSessionPolicy.isCurrentGeneration(4L, 5L))
        assertTrue(NativeProcessSessionPolicy.isCurrentGeneration(5L, 5L))
    }

    @Test fun acceptsHandshakeOnlyForCurrentGenerationAndIsolatedPid() {
        assertTrue(NativeProcessSessionPolicy.isValidHandshake(5L, 5L, 8123, 100))
        assertFalse(NativeProcessSessionPolicy.isValidHandshake(4L, 5L, 8123, 100))
        assertFalse(NativeProcessSessionPolicy.isValidHandshake(5L, 5L, 100, 100))
        assertFalse(NativeProcessSessionPolicy.isValidHandshake(5L, 5L, -1, 100))
    }

    @Test fun rejectsResponsesFromDifferentProcessIncarnation() {
        assertTrue(NativeProcessSessionPolicy.isSameProcess("session-a", "session-a"))
        assertFalse(NativeProcessSessionPolicy.isSameProcess("session-a", "session-b"))
        assertFalse(NativeProcessSessionPolicy.isSameProcess("session-a", null))
    }

    @Test fun watchdogMayKillOnlyTheExactCurrentSession() {
        assertTrue(NativeProcessSessionPolicy.mayTerminate(
            expectedGeneration = 8L, currentGeneration = 8L,
            expectedPid = 8123, currentPid = 8123,
            expectedSession = "session-a", currentSession = "session-a"
        ))
        assertFalse(NativeProcessSessionPolicy.mayTerminate(
            8L, 9L, 8123, 8123, "session-a", "session-a"
        ))
        assertFalse(NativeProcessSessionPolicy.mayTerminate(
            8L, 8L, 8123, 9000, "session-a", "session-a"
        ))
        assertFalse(NativeProcessSessionPolicy.mayTerminate(
            8L, 8L, 8123, 8123, "session-a", "session-b"
        ))
    }

    @Test fun neverAcceptsMainProcessAsNativeProcess() {
        assertFalse(NativeProcessSessionPolicy.isValidHandshake(1L, 1L, 100, 100))
        assertEquals(false, NativeProcessSessionPolicy.isSameProcess("", ""))
    }
}
