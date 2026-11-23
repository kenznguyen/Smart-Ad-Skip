package com.kenznguyen.smartadskip.accessibility

/**
 * Requires repeated, high-confidence visual evidence before an action can be considered.
 * A confirmed action is emitted once; an UNKNOWN frame resets the latch.
 */
class VisualActionTemporalVerifier(
    private val minimumConfidence: Float = 0.82f,
    private val confirmationFrames: Int = 2,
    private val maximumFrameGapMillis: Long = 750L
) {
    private var candidate: VisualActionType? = null
    private var consecutiveFrames = 0
    private var lastTimestampMillis: Long? = null
    private var emitted = false

    init {
        require(minimumConfidence in 0f..1f)
        require(confirmationFrames >= 2)
        require(maximumFrameGapMillis > 0L)
    }

    @Synchronized
    fun observe(match: VisualActionMatch, timestampMillis: Long): VisualActionType? {
        val previousTimestamp = lastTimestampMillis
        if (timestampMillis <= 0L ||
            (previousTimestamp != null && timestampMillis <= previousTimestamp)
        ) {
            reset()
            return null
        }

        if (match.type == VisualActionType.UNKNOWN ||
            match.confidence < minimumConfidence ||
            match.confidence > 1f ||
            match.confidence < 0f
        ) {
            reset()
            return null
        }

        val sameCandidate = candidate == match.type
        val withinWindow = previousTimestamp != null &&
            timestampMillis - previousTimestamp <= maximumFrameGapMillis

        if (!sameCandidate || !withinWindow) {
            candidate = match.type
            consecutiveFrames = 1
            emitted = false
        } else {
            consecutiveFrames++
        }
        lastTimestampMillis = timestampMillis

        if (consecutiveFrames >= confirmationFrames && !emitted) {
            emitted = true
            return match.type
        }
        return null
    }

    @Synchronized
    fun reset() {
        candidate = null
        consecutiveFrames = 0
        lastTimestampMillis = null
        emitted = false
    }
}
