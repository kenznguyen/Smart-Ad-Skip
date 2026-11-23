package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

/**
 * The classifier is the sole authority for ad-context evidence.
 * Visual matches and Resource IDs cannot independently authorize a click.
 */
class ActionExecutionPolicy {
    fun canClick(adState: AdState): Boolean =
        adState == AdState.AD_PLAYING || adState == AdState.SKIP_AVAILABLE
}
