package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

/**
 * Prevents automatic interaction when the ad context is unknown or a known rewarded
 * video activity is active/has just transitioned away.
 */
class ActionExecutionPolicy {
    fun canClick(
        adState: AdState,
        currentWindowClassName: String?,
        previousWindowClassName: String?
    ): Boolean {
        if (adState == AdState.UNKNOWN) return false
        if (currentWindowClassName in REWARDED_AD_ACTIVITY_CLASSES) return false
        if (
            adState == AdState.AD_COMPLETED &&
            previousWindowClassName in REWARDED_AD_ACTIVITY_CLASSES
        ) return false
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
