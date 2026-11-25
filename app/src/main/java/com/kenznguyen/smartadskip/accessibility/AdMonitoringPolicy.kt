package com.kenznguyen.smartadskip.accessibility

/**
 * Bounds automatic re-observation after a model WAIT decision.
 * Only a verified target foreground and a positively classified ad may consume a cycle.
 */
internal class AdMonitoringPolicy(
    private val maxCycles: Int = DEFAULT_MAX_CYCLES,
    private val initialDelayMillis: Long = DEFAULT_INITIAL_DELAY_MILLIS,
    private val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS
) {
    private var scheduledCycles = 0

    val hasRemainingCycles: Boolean
        get() = scheduledCycles < maxCycles.coerceAtLeast(0)

    fun nextDelayMillis(
        isTargetForeground: Boolean,
        isKnownAdState: Boolean
    ): Long? {
        if (!isTargetForeground || !isKnownAdState || !hasRemainingCycles) return null

        val exponent = scheduledCycles.coerceAtMost(MAX_BACKOFF_EXPONENT)
        val multiplier = 1L shl exponent
        val delay = safeMultiply(initialDelayMillis.coerceAtLeast(1L), multiplier)
            .coerceAtMost(maxDelayMillis.coerceAtLeast(1L))
        scheduledCycles += 1
        return delay
    }

    fun reset() {
        scheduledCycles = 0
    }

    private fun safeMultiply(value: Long, multiplier: Long): Long =
        if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier

    private companion object {
        const val DEFAULT_MAX_CYCLES = 6
        const val DEFAULT_INITIAL_DELAY_MILLIS = 1_500L
        const val DEFAULT_MAX_DELAY_MILLIS = 8_000L
        const val MAX_BACKOFF_EXPONENT = 20
    }
}
