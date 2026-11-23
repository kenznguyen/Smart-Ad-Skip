package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisualActionTemporalVerifierTest {
    private val verifier = VisualActionTemporalVerifier(
        minimumConfidence = 0.82f,
        confirmationFrames = 2,
        maximumFrameGapMillis = 750L
    )

    @Test
    fun requiresTwoConsecutiveHighConfidenceFrames() {
        assertNull(verifier.observe(match(VisualActionType.NEXT_AD, 0.95f), 1_000L))
        assertEquals(
            VisualActionType.NEXT_AD,
            verifier.observe(match(VisualActionType.NEXT_AD, 0.93f), 1_300L)
        )
    }

    @Test
    fun rejectsLowConfidenceFrames() {
        assertNull(verifier.observe(match(VisualActionType.CLOSE, 0.81f), 1_000L))
        assertNull(verifier.observe(match(VisualActionType.CLOSE, 0.99f), 1_200L))
    }

    @Test
    fun resetsConfirmationWhenActionChanges() {
        assertNull(verifier.observe(match(VisualActionType.NEXT_AD, 0.95f), 1_000L))
        assertNull(verifier.observe(match(VisualActionType.CLOSE, 0.95f), 1_200L))
        assertEquals(
            VisualActionType.CLOSE,
            verifier.observe(match(VisualActionType.CLOSE, 0.95f), 1_400L)
        )
    }

    @Test
    fun resetsWhenFramesAreTooFarApart() {
        assertNull(verifier.observe(match(VisualActionType.NEXT_AD, 0.95f), 1_000L))
        assertNull(verifier.observe(match(VisualActionType.NEXT_AD, 0.95f), 1_751L))
    }

    @Test
    fun emitsOnlyOnceUntilUnknownFrameResetsTheLatch() {
        assertNull(verifier.observe(match(VisualActionType.CLOSE, 0.95f), 1_000L))
        assertEquals(VisualActionType.CLOSE, verifier.observe(match(VisualActionType.CLOSE, 0.95f), 1_200L))
        assertNull(verifier.observe(match(VisualActionType.CLOSE, 0.95f), 1_400L))
        assertNull(verifier.observe(match(VisualActionType.UNKNOWN, 0f), 1_500L))
        assertNull(verifier.observe(match(VisualActionType.CLOSE, 0.95f), 1_600L))
        assertEquals(VisualActionType.CLOSE, verifier.observe(match(VisualActionType.CLOSE, 0.95f), 1_700L))
    }

    @Test
    fun rejectsOutOfOrderTimestamps() {
        assertNull(verifier.observe(match(VisualActionType.NEXT_AD, 0.95f), 2_000L))
        assertNull(verifier.observe(match(VisualActionType.NEXT_AD, 0.95f), 1_900L))
    }

    private fun match(type: VisualActionType, confidence: Float) =
        VisualActionMatch(type, confidence)
}
