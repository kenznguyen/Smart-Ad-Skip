package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState

enum class CloseButtonDetectionStatus {
    DETECTED,
    AD_NOT_COMPLETED,
    STALE_SNAPSHOT,
    WRONG_PACKAGE,
    NO_UNIQUE_CLOSE_CONTROL
}

data class CloseButtonCandidate(
    val resourceId: String,
    val viewClassName: String?,
    val bounds: ScreenBounds
)

data class CloseButtonDiagnostics(
    val status: CloseButtonDetectionStatus,
    val candidate: CloseButtonCandidate? = null
)

/**
 * Identifies a visible Close/Dismiss control after the existing classifier confirms
 * that an observed ad has completed. This class never performs UI actions.
 */
class CloseButtonDetector(
    private val staleAfterMillis: Long = 15_000L,
    private val targetPackageName: String = "com.funnyvideo.cgame.fruitblast"
) {
    init {
        require(staleAfterMillis > 0L)
    }

    fun detect(
        current: AccessibilitySnapshot,
        previous: AccessibilitySnapshot?,
        adState: AdState,
        nowEpochMillis: Long
    ): CloseButtonDiagnostics {
        if (current.packageName != targetPackageName ||
            previous?.packageName != targetPackageName
        ) {
            return CloseButtonDiagnostics(CloseButtonDetectionStatus.WRONG_PACKAGE)
        }

        if (adState != AdState.AD_COMPLETED) {
            return CloseButtonDiagnostics(CloseButtonDetectionStatus.AD_NOT_COMPLETED)
        }

        if (previous == null ||
            previous.observedAtEpochMillis >= current.observedAtEpochMillis ||
            !isFresh(previous.observedAtEpochMillis, nowEpochMillis) ||
            !isFresh(current.observedAtEpochMillis, nowEpochMillis)
        ) {
            return CloseButtonDiagnostics(CloseButtonDetectionStatus.STALE_SNAPSHOT)
        }

        val candidates = current.structuralNodes.filter(::isCloseControl)
        if (candidates.size != 1) {
            return CloseButtonDiagnostics(CloseButtonDetectionStatus.NO_UNIQUE_CLOSE_CONTROL)
        }

        val node = candidates.single()
        return CloseButtonDiagnostics(
            status = CloseButtonDetectionStatus.DETECTED,
            candidate = CloseButtonCandidate(
                resourceId = node.resourceId.orEmpty(),
                viewClassName = node.viewClassName,
                bounds = node.bounds
            )
        )
    }

    private fun isFresh(timestamp: Long, now: Long): Boolean {
        val age = now - timestamp
        return timestamp > 0L && age in 0..staleAfterMillis
    }

    private fun isCloseControl(node: StructuralNodeMetadata): Boolean {
        if (!node.clickable || !node.bounds.hasArea) return false
        val id = node.resourceId?.lowercase() ?: return false
        return CLOSE_RESOURCE_ID_PATTERN.containsMatchIn(id)
    }

    private companion object {
        val CLOSE_RESOURCE_ID_PATTERN =
            Regex("(?:^|[/_:])(?:btn_)?(?:close|dismiss)(?:[_:/]|$)")
    }
}
