package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionExecutionPolicyTest {
    private val policy = ActionExecutionPolicy()

    @Test
    fun permitsClicksOnlyWhileRecognizedAdIsActiveAndNotRewarded() {
        assertTrue(policy.canClick(AdState.AD_PLAYING, "GameAdActivity", null, false, false))
        assertTrue(policy.canClick(AdState.SKIP_AVAILABLE, "GameAdActivity", null, false, false))
    }

    @Test
    fun blocksUnknownAndCompletedAdContexts() {
        assertFalse(policy.canClick(AdState.UNKNOWN, "GameActivity", null, false, false))
        assertFalse(policy.canClick(AdState.AD_COMPLETED, "GameActivity", "InterstitialActivity", false, false))
    }

    @Test
    fun rejectsKnownRewardedAdActivity() {
        assertFalse(
            policy.canClick(
                AdState.AD_PLAYING,
                "com.google.android.gms.ads.AdActivity",
                null,
                false,
                false
            )
        )
    }

    @Test
    fun rejectsRewardedAdResourceMarkerEvenWithUnknownActivityClass() {
        assertFalse(policy.canClick(AdState.AD_PLAYING, "GameAdActivity", null, true, false))
        assertFalse(policy.canClick(AdState.SKIP_AVAILABLE, "GameAdActivity", null, false, true))
    }

    @Test
    fun rejectsTransitionImmediatelyAfterKnownRewardedActivity() {
        assertFalse(
            policy.canClick(
                AdState.SKIP_AVAILABLE,
                "GameAdActivity",
                "com.mbridge.msdk.reward.player.MBRewardVideoActivity",
                false,
                false
            )
        )
    }
}
