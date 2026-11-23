package com.kenznguyen.smartadskip.detection

import com.kenznguyen.smartadskip.accessibility.AccessibilitySnapshot

enum class AdState {
    AD_PLAYING,
    SKIP_AVAILABLE,
    AD_COMPLETED,
    UNKNOWN
}

/**
 * Conservative Fruit Blast ad-context classifier.
 * Resource IDs and view labels are deliberately not consulted.
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
        previous: AccessibilitySnapshot? = null,
        hasVerifiedVisualAction: Boolean = false
    ): AdState {
        if (!isRelevantPackage(current.packageName)) return AdState.UNKNOWN
        if (!isFresh(current.observedAtEpochMillis, nowEpochMillis)) return AdState.UNKNOWN
        if (current.structuralNodes.isEmpty()) return AdState.UNKNOWN

        if (isKnownAdSdkActivity(current.windowClassName)) {
            return if (hasVerifiedVisualAction) AdState.SKIP_AVAILABLE else AdState.AD_PLAYING
        }
        if (previous != null &&
            previous.packageName == current.packageName &&
            isRelevantPackage(previous.packageName) &&
            previous.observedAtEpochMillis < current.observedAtEpochMillis &&
            isFresh(previous.observedAtEpochMillis, nowEpochMillis) &&
            previous.structuralNodes.isNotEmpty() &&
            isKnownAdSdkActivity(previous.windowClassName)
        ) return AdState.AD_COMPLETED

        return AdState.UNKNOWN
    }

    private fun isKnownAdSdkActivity(windowClassName: String?): Boolean =
        windowClassName in AD_SDK_ACTIVITY_CLASSES

    private fun isFresh(timestamp: Long, now: Long): Boolean {
        val age = now - timestamp
        return timestamp > 0L && age in 0..staleAfterMillis
    }

    private companion object {
        const val TARGET_APP_PACKAGE = "com.funnyvideo.cgame.fruitblast"
        val AD_SDK_ACTIVITY_CLASSES = setOf(
            "com.mbridge.msdk.reward.player.MBRewardVideoActivity",
            "com.google.android.gms.ads.AdActivity",
            "com.unity3d.services.ads.adunit.AdUnitActivity",
            "com.applovin.adview.AppLovinFullscreenActivity",
            "com.facebook.ads.AudienceNetworkActivity"
        )
    }
}
