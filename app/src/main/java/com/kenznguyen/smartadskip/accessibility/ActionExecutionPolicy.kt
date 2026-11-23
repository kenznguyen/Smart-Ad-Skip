package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

/**
 * Final action allowlist for Click Authorization.
 * Candidate discovery (OCR or symbol classification) is not sufficient to click.
 * The service must first pass package, freshness, and live Accessibility checks.
 * Completed ad contexts are always blocked.
 */
class ActionExecutionPolicy {
    fun canClick(adState: AdState, detectedAction: VisualActionType): Boolean =
        adState != AdState.AD_COMPLETED &&
            detectedAction in APPROVED_ACTIONS

    private companion object {
        val APPROVED_ACTIONS = setOf(
            VisualActionType.NEXT_AD,
            VisualActionType.CLOSE
        )
    }
}
