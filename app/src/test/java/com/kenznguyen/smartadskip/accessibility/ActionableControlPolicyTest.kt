package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionableControlPolicyTest {
    private val policy = ActionableControlPolicy()

    @Test
    fun classifiesExplicitNextAdControl() {
        assertEquals(ActionableControlType.NEXT_AD, policy.classify("com.game:id/next_ad_button"))
    }

    @Test
    fun classifiesExplicitCloseControl() {
        assertEquals(ActionableControlType.CLOSE, policy.classify("com.game:id/ad_close"))
    }

    @Test
    fun classifiesExplicitContinueControl() {
        assertEquals(ActionableControlType.CONTINUE, policy.classify("com.game:id/continue_button"))
    }

    @Test
    fun rejectsAdSurfaceAndInstallControls() {
        assertEquals(ActionableControlType.PROTECTED, policy.classify("com.game:id/ad_container"))
        assertEquals(ActionableControlType.PROTECTED, policy.classify("com.game:id/install_cta"))
    }

    @Test
    fun rejectsRewardClaimAndCollectControls() {
        assertEquals(ActionableControlType.PROTECTED, policy.classify("com.game:id/claim_reward"))
        assertEquals(ActionableControlType.PROTECTED, policy.classify("com.game:id/collect_button"))
    }

    @Test
    fun unknownOrMissingResourceIdsAreNeverClickable() {
        assertEquals(ActionableControlType.UNKNOWN, policy.classify(null))
        assertEquals(ActionableControlType.UNKNOWN, policy.classify("com.game:id/button_17"))
        assertEquals(ActionableControlType.UNKNOWN, policy.classify("com.game:id/next_button"))
    }

    @Test
    fun protectedMeaningTakesPrecedenceOverNavigationTokens() {
        assertEquals(ActionableControlType.PROTECTED, policy.classify("com.game:id/continue_reward"))
        assertEquals(ActionableControlType.PROTECTED, policy.classify("com.game:id/next_install"))
    }
}
