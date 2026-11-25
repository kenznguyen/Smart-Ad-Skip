package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutionPolicyTest {
    private val policy = ActionExecutionPolicy()

    @Test
    fun blocksEveryActionWhenAdStateIsUnknown() {
        assertFalse(policy.canClick(AdState.UNKNOWN, VisualActionType.NEXT_AD))
        assertFalse(policy.canClick(AdState.UNKNOWN, VisualActionType.CLOSE))
        assertFalse(policy.canClick(AdState.UNKNOWN, VisualActionType.CONTINUE))
    }

    @Test
    fun blocksUnknownVisualActionInConfirmedAdContext() {
        assertFalse(policy.canClick(AdState.AD_PLAYING, VisualActionType.UNKNOWN))
        assertFalse(policy.canClick(AdState.SKIP_AVAILABLE, VisualActionType.UNKNOWN))
        assertFalse(policy.canClick(AdState.AD_PAUSED, VisualActionType.UNKNOWN))
    }

    @Test
    fun permitsOnlyApprovedActionsInConfirmedAdContext() {
        assertTrue(policy.canClick(AdState.AD_PLAYING, VisualActionType.NEXT_AD))
        assertTrue(policy.canClick(AdState.AD_PLAYING, VisualActionType.CLOSE))
        assertTrue(policy.canClick(AdState.SKIP_AVAILABLE, VisualActionType.NEXT_AD))
        assertTrue(policy.canClick(AdState.SKIP_AVAILABLE, VisualActionType.CLOSE))
        assertFalse(policy.canClick(AdState.AD_PLAYING, VisualActionType.CONTINUE))
        assertFalse(policy.canClick(AdState.SKIP_AVAILABLE, VisualActionType.CONTINUE))
    }

    @Test
    fun continueIsAllowedOnlyWhenAiHasConfirmedPausedAdState() {
        assertTrue(policy.canClick(AdState.AD_PAUSED, VisualActionType.CONTINUE))
        assertFalse(policy.canClick(AdState.AD_PAUSED, VisualActionType.NEXT_AD))
        assertFalse(policy.canClick(AdState.AD_PAUSED, VisualActionType.CLOSE))
    }

    @Test
    fun gameHomeIsNeverAnActionableAdState() {
        assertFalse(policy.canClick(AdState.GAME_HOME, VisualActionType.NEXT_AD))
        assertFalse(policy.canClick(AdState.GAME_HOME, VisualActionType.CLOSE))
        assertFalse(policy.canClick(AdState.GAME_HOME, VisualActionType.CONTINUE))
    }

    @Test
    fun allowsOnlyExplicitlyConfirmedAdStates() {
        assertFalse(policy.canClick(AdState.UNKNOWN, VisualActionType.NEXT_AD))
        assertFalse(policy.canClick(AdState.AD_COMPLETED, VisualActionType.CLOSE))
        assertTrue(policy.canClick(AdState.AD_PLAYING, VisualActionType.NEXT_AD))
        assertTrue(policy.canClick(AdState.SKIP_AVAILABLE, VisualActionType.CLOSE))
    }

    @Test
    fun alwaysBlocksCompletedAdContext() {
        assertFalse(policy.canClick(AdState.AD_COMPLETED, VisualActionType.NEXT_AD))
        assertFalse(policy.canClick(AdState.AD_COMPLETED, VisualActionType.CLOSE))
        assertFalse(policy.canClick(AdState.AD_COMPLETED, VisualActionType.CONTINUE))
    }
}
