package com.kenznguyen.smartadskip.detection

import com.kenznguyen.smartadskip.accessibility.AccessibilitySnapshot
import com.kenznguyen.smartadskip.accessibility.StructuralNodeMetadata

enum class AdState {
    AD_PLAYING,
    SKIP_AVAILABLE,
    AD_COMPLETED,
    UNKNOWN
}

/**
 * Conservative, observe-only classifier for the current Fruit Blast validation target.
 * Events from System UI and screen-recorder packages are deliberately excluded.
 */
class AdStateClassifier(
    private val staleAfterMillis: Long = 15_000L,
    private val targetPackageName: String = TARGET_APP_PACKAGE
) {
    fun isRelevantPackage(packageName: String?): Boolean =
        packageName == targetPackageName

    fun classify(
        current: AccessibilitySnapshot,
        nowEpochMillis: Long,
        previous: AccessibilitySnapshot? = null
    ): AdState {
        if (!isRelevantPackage(current.packageName)) return AdState.UNKNOWN
        if (!isFresh(current.observedAtEpochMillis, nowEpochMillis)) return AdState.UNKNOWN
        if (current.structuralNodes.isEmpty()) return AdState.UNKNOWN

        if (isKnownRewardedAdActivity(current.windowClassName)) return AdState.AD_PLAYING

        val hasAdContainer = current.structuralNodes.any(::isAdContainer)
        val hasSkipControl = current.structuralNodes.any(::isSkipControl)

        if (hasAdContainer && hasSkipControl) return AdState.SKIP_AVAILABLE
        if (hasAdContainer) return AdState.AD_PLAYING

        if (previous != null &&
            previous.packageName == current.packageName &&
            isRelevantPackage(previous.packageName) &&
            previous.observedAtEpochMillis < current.observedAtEpochMillis &&
            isFresh(previous.observedAtEpochMillis, nowEpochMillis) &&
            previous.structuralNodes.isNotEmpty() &&
            (previous.structuralNodes.any(::isAdContainer) ||
                isKnownRewardedAdActivity(previous.windowClassName))
        ) {
            return AdState.AD_COMPLETED
        }

        return AdState.UNKNOWN
    }

    private fun isKnownRewardedAdActivity(windowClassName: String?): Boolean =
        windowClassName in REWARDED_AD_ACTIVITY_CLASSES

    private fun isFresh(timestamp: Long, now: Long): Boolean {
        val age = now - timestamp
        return timestamp > 0L && age in 0..staleAfterMillis
    }

    private fun isAdContainer(node: StructuralNodeMetadata): Boolean {
        val id = node.resourceId?.lowercase() ?: return false
        return AD_RESOURCE_MARKERS.any { marker -> id.contains(marker) } && node.bounds.hasArea
    }

    private fun isSkipControl(node: StructuralNodeMetadata): Boolean {
        val id = node.resourceId?.lowercase() ?: return false
        return node.clickable && node.bounds.hasArea && id.contains("skip")
    }

    private companion object {
        const val TARGET_APP_PACKAGE = "com.funnyvideo.cgame.fruitblast"
        val REWARDED_AD_ACTIVITY_CLASSES = setOf(
            "com.mbridge.msdk.reward.player.MBRewardVideoActivity",
            "com.google.android.gms.ads.AdActivity",
            "com.unity3d.services.ads.adunit.AdUnitActivity",
            "com.applovin.adview.AppLovinFullscreenActivity",
            "com.facebook.ads.AudienceNetworkActivity"
        )
        val AD_RESOURCE_MARKERS = listOf("ad_container", "ad_view", "interstitial_ad", "rewarded_ad")
    }
}
