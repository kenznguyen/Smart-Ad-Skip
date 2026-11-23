package com.kenznguyen.smartadskip.accessibility

/**
 * Reports currently actionable Accessibility nodes from a fresh target-app snapshot.
 * Detection is independent of ad classification and never performs UI actions.
 */
class ActionableNodeDetector(
    private val staleAfterMillis: Long = 15_000L,
    private val targetPackageName: String = "com.funnyvideo.cgame.fruitblast",
    private val maxCandidates: Int = 20
) {
    init {
        require(staleAfterMillis > 0L)
        require(maxCandidates > 0)
    }

    fun detect(
        current: AccessibilitySnapshot,
        nowEpochMillis: Long
    ): ActionableNodeDiagnostics {
        if (current.packageName != targetPackageName) {
            return ActionableNodeDiagnostics(ActionableNodeDetectionStatus.WRONG_PACKAGE)
        }

        if (!isFresh(current.observedAtEpochMillis, nowEpochMillis)) {
            return ActionableNodeDiagnostics(ActionableNodeDetectionStatus.STALE_SNAPSHOT)
        }

        val candidates = current.structuralNodes
            .asSequence()
            .filter { node ->
                node.clickable &&
                    node.enabled &&
                    node.visible &&
                    node.bounds.hasArea
            }
            .take(maxCandidates)
            .map { node ->
                ActionableNodeCandidate(bounds = node.bounds)
            }
            .toList()

        return if (candidates.isEmpty()) {
            ActionableNodeDiagnostics(ActionableNodeDetectionStatus.NO_ACTIONABLE_NODES)
        } else {
            ActionableNodeDiagnostics(
                status = ActionableNodeDetectionStatus.DETECTED,
                candidates = candidates
            )
        }
    }

    private fun isFresh(timestamp: Long, now: Long): Boolean {
        val age = now - timestamp
        return timestamp > 0L && age in 0..staleAfterMillis
    }
}
