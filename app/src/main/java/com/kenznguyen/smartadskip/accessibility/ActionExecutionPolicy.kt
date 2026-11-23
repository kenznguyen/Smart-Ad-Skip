package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

/**
 * Final Close-only allowlist. Candidate discovery is never sufficient to click.
 * Exact package, freshness, live Accessibility and Strict Safety are checked by the service.
 */
class ActionExecutionPolicy {
    fun canClick(adState: AdState, detectedAction: VisualActionType): Boolean =
        adState != AdState.AD_COMPLETED && detectedAction == VisualActionType.CLOSE
}
