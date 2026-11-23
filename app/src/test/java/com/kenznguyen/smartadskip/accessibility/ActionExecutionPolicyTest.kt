package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutionPolicyTest {
    private val policy = ActionExecutionPolicy()

    @Test
    fun permitsKnownNonRewardedAdStates() {
        assertTrue(policy.canClick(AdState.AD_PLAYING, "GameActivity", null))
        assertTrue(policy.canClick(AdState.SKIP_AVAILABLE, "GameActivity", null))
        assertTrue(policy.canClick(AdState.AD_COMPLETED, "GameActivity", "InterstitialActivity"))
    }

    @Test
    fun rejectsUnknownAdContext() {
        assertFalse(policy.canClick(AdState.UNKNOWN, "GameActivity", null))
    }

    @Test
    fun rejectsKnownRewardedAdActivity() {
        assertFalse(
            policy.canClick(
                AdState.AD_PLAYING,
                "com.google.android.gms.ads.AdActivity",
                null
            )
        )
    }

    @Test
    fun rejectsCompletionImmediatelyAfterKnownRewardedActivity() {
        assertFalse(
            policy.canClick(
                AdState.AD_COMPLETED,
                "GameActivity",
                "com.mbridge.msdk.reward.player.MBRewardVideoActivity"
            )
        )
    }
}
