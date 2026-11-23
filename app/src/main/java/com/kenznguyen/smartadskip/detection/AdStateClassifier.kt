package com.kenznguyen.smartadskip.detection

import com.kenznguyen.smartadskip.accessibility.AccessibilitySnapshot
import com.kenznguyen.smartadskip.accessibility.StructuralNodeMetadata

enum class AdState {
    AD_PLAYING,
    SKIP_AVAILABLE,
    AD_COMPLETED,
    UNKNOWN
}

class AdStateClassifier(
    private val staleAfterMillis: Long = 15_000L
) {
    fun classify(
        current: AccessibilitySnapshot,
        nowEpochMillis: Long,
        previous: AccessibilitySnapshot? = null
    ): AdState {
        if (!isFresh(current.observedAtEpochMillis, nowEpochMillis)) return AdState.UNKNOWN
        if (current.structuralNodes.isEmpty()) return AdState.UNKNOWN

        val hasAdContainer = current.structuralNodes.any(::isAdContainer)
        val hasSkipControl = current.structuralNodes.any(::isSkipControl)

        if (hasAdContainer && hasSkipControl) return AdState.SKIP_AVAILABLE
        if (hasAdContainer) return AdState.AD_PLAYING

        if (previous != null &&
            previous.packageName == current.packageName &&
            previous.packageName != null &&
            previous.observedAtEpochMillis < current.observedAtEpochMillis &&
            isFresh(previous.observedAtEpochMillis, nowEpochMillis) &&
            previous.structuralNodes.any(::isAdContainer)
        ) {
            return AdState.AD_COMPLETED
        }

        return AdState.UNKNOWN
    }

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
        val AD_RESOURCE_MARKERS = listOf("ad_container", "ad_view", "interstitial_ad", "rewarded_ad")
    }
}
