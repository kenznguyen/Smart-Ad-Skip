package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

/**
 * Final action allowlist for Click Authorization.
 * AI output never overrides the requirement for a confirmed ad context.
 */
class ActionExecutionPolicy {
    fun canClick(adState: AdState, detectedAction: VisualActionType): Boolean =
        when (adState) {
            AdState.AD_PLAYING, AdState.SKIP_AVAILABLE ->
                detectedAction in APPROVED_ACTIONS
            AdState.AD_COMPLETED, AdState.UNKNOWN -> false
        }

    private companion object {
        val APPROVED_ACTIONS = setOf(
            VisualActionType.NEXT_AD,
            VisualActionType.CLOSE
        )
    }
}
