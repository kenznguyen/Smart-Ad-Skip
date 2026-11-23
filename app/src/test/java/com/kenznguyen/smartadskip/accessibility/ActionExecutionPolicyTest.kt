package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutionPolicyTest {
    private val policy = ActionExecutionPolicy()

    @Test
    fun permitsOnlyCloseInUnknownOrActiveAdState() {
        assertTrue(policy.canClick(AdState.UNKNOWN, VisualActionType.CLOSE))
        assertTrue(policy.canClick(AdState.AD_PLAYING, VisualActionType.CLOSE))
        assertTrue(policy.canClick(AdState.SKIP_AVAILABLE, VisualActionType.CLOSE))
    }

    @Test
    fun rejectsNextAdAndUnknownActionsInEveryState() {
        listOf(AdState.UNKNOWN, AdState.AD_PLAYING, AdState.SKIP_AVAILABLE).forEach { state ->
            assertFalse(policy.canClick(state, VisualActionType.NEXT_AD))
            assertFalse(policy.canClick(state, VisualActionType.UNKNOWN))
        }
    }

    @Test
    fun alwaysBlocksCompletedAdContext() {
        assertFalse(policy.canClick(AdState.AD_COMPLETED, VisualActionType.NEXT_AD))
        assertFalse(policy.canClick(AdState.AD_COMPLETED, VisualActionType.CLOSE))
    }
}
