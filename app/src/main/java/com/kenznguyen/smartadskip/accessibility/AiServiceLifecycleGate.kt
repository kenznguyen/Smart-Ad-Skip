package com.kenznguyen.smartadskip.accessibility

/**
 * Invalidates callbacks from a disconnected Accessibility Service and allows one
 * AI runtime initialization attempt per connected generation.
 */
internal class AiServiceLifecycleGate {
    private var generation = 0L
    private var connected = false
    private var initializationStarted = false

    @Synchronized
    fun connect(): Long {
        generation += 1
        connected = true
        initializationStarted = false
        return generation
    }

    @Synchronized
    fun disconnect(): Long {
        generation += 1
        connected = false
        initializationStarted = false
        return generation
    }

    @Synchronized
    fun tryBeginInitialization(requestGeneration: Long): Boolean {
        if (!connected || requestGeneration != generation || initializationStarted) return false
        initializationStarted = true
        return true
    }

    @Synchronized
    fun isCurrent(requestGeneration: Long): Boolean =
        connected && requestGeneration == generation
}
