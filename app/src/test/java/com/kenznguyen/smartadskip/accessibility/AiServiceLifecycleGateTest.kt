package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiServiceLifecycleGateTest {
    @Test
    fun reconnectAllowsExactlyOneFreshInitialization() {
        val gate = AiServiceLifecycleGate()
        val firstGeneration = gate.connect()

        assertTrue(gate.tryBeginInitialization(firstGeneration))
        assertFalse(gate.tryBeginInitialization(firstGeneration))

        gate.disconnect()
        val secondGeneration = gate.connect()

        assertTrue(gate.isCurrent(secondGeneration))
        assertFalse(gate.isCurrent(firstGeneration))
        assertTrue(gate.tryBeginInitialization(secondGeneration))
        assertFalse(gate.tryBeginInitialization(secondGeneration))
    }

    @Test
    fun disconnectInvalidatesOutstandingLoadCallback() {
        val gate = AiServiceLifecycleGate()
        val generation = gate.connect()
        assertTrue(gate.tryBeginInitialization(generation))

        gate.disconnect()

        assertFalse(gate.isCurrent(generation))
        assertFalse(gate.tryBeginInitialization(generation))
    }
}
