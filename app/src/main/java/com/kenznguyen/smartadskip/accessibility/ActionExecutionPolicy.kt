package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

/**
 * Ad-state UNKNOWN is not an independent denial when a supported visual action
 * has passed single-frame Visual Detection and OCR corroboration. Completed contexts remain blocked.
 */
class ActionExecutionPolicy {
    fun canClick(adState: AdState, verifiedAction: VisualActionType): Boolean =
        adState != AdState.AD_COMPLETED &&
            verifiedAction in APPROVED_VISUAL_ACTIONS

    private companion object {
        val APPROVED_VISUAL_ACTIONS = setOf(
            VisualActionType.NEXT_AD,
            VisualActionType.CLOSE
        )
    }
}
