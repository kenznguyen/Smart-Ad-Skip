package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

/**
 * Allows clicks only while a recognized ad is actively on screen.
 * Unknown/completed contexts and known rewarded-video activities are blocked.
 */
class ActionExecutionPolicy {
    fun canClick(
        adState: AdState,
        currentWindowClassName: String?,
        previousWindowClassName: String?
    ): Boolean {
        if (adState !in setOf(AdState.AD_PLAYING, AdState.SKIP_AVAILABLE)) return false
        if (currentWindowClassName in REWARDED_AD_ACTIVITY_CLASSES) return false
        if (previousWindowClassName in REWARDED_AD_ACTIVITY_CLASSES) return false
        return true
    }

    private companion object {
        val REWARDED_AD_ACTIVITY_CLASSES = setOf(
            "com.mbridge.msdk.reward.player.MBRewardVideoActivity",
            "com.google.android.gms.ads.AdActivity",
            "com.unity3d.services.ads.adunit.AdUnitActivity",
            "com.applovin.adview.AppLovinFullscreenActivity",
            "com.facebook.ads.AudienceNetworkActivity"
        )
    }
}
