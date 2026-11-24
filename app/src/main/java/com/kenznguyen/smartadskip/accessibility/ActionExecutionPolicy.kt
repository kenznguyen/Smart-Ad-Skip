package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

/**
 * Final action allowlist for Click Authorization.
 * The AI selects the action; this policy only applies the final action allowlist
 * and blocks known completed-ad contexts after live Accessibility re-identification.
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
