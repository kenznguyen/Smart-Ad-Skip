package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutionPolicyTest {
    private val policy = ActionExecutionPolicy()

    @Test
    fun permitsApprovedVisuallyConfirmedActionsInUnknownAdState() {
        assertTrue(policy.canClick(AdState.UNKNOWN, VisualActionType.NEXT_AD))
        assertTrue(policy.canClick(AdState.UNKNOWN, VisualActionType.CLOSE))
    }

    @Test
    fun blocksUnknownVisualActionEvenWhenAdStateIsUnknown() {
        assertFalse(policy.canClick(AdState.UNKNOWN, VisualActionType.UNKNOWN))
    }

    @Test
    fun permitsApprovedActionsInClassifierConfirmedActiveAdContext() {
        assertTrue(policy.canClick(AdState.AD_PLAYING, VisualActionType.NEXT_AD))
        assertTrue(policy.canClick(AdState.SKIP_AVAILABLE, VisualActionType.CLOSE))
    }

    @Test
    fun alwaysBlocksCompletedAdContext() {
        assertFalse(policy.canClick(AdState.AD_COMPLETED, VisualActionType.NEXT_AD))
        assertFalse(policy.canClick(AdState.AD_COMPLETED, VisualActionType.CLOSE))
    }
}
