package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

/**
 * Final action allowlist for Click Authorization.
 * AI output never overrides the requirement for a confirmed ad context.
 */
class ActionExecutionPolicy {
    fun canClick(adState: AdState, detectedAction: VisualActionType): Boolean =
        adState != AdState.UNKNOWN &&
            adState != AdState.AD_COMPLETED &&
            detectedAction in APPROVED_ACTIONS

    private companion object {
        val APPROVED_ACTIONS = setOf(
            VisualActionType.NEXT_AD,
            VisualActionType.CLOSE
        )
    }
}
