package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutionPolicyTest {
    private val policy = ActionExecutionPolicy()

    @Test
    fun permitsClicksOnlyForClassifierConfirmedActiveAdContext() {
        assertTrue(policy.canClick(AdState.AD_PLAYING))
        assertTrue(policy.canClick(AdState.SKIP_AVAILABLE))
    }

    @Test
    fun blocksUnknownAndCompletedAdContexts() {
        assertFalse(policy.canClick(AdState.UNKNOWN))
        assertFalse(policy.canClick(AdState.AD_COMPLETED))
    }
}
