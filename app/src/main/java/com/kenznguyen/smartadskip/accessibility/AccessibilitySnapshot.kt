package com.kenznguyen.smartadskip.accessibility

data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val hasArea: Boolean get() = right > left && bottom > top
}

data class StructuralNodeMetadata(
    val viewClassName: String?,
    val resourceId: String?,
    val clickable: Boolean,
    val bounds: ScreenBounds,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val text: String? = null,
    val contentDescription: String? = null,
    val childCount: Int = 0
)

data class ActionableNodeCandidate(
    val bounds: ScreenBounds
)

enum class ActionableNodeDetectionStatus {
    DETECTED,
    NO_ACTIONABLE_NODES,
    STALE_SNAPSHOT,
    WRONG_PACKAGE
}

data class ActionableNodeDiagnostics(
    val status: ActionableNodeDetectionStatus,
    val candidates: List<ActionableNodeCandidate> = emptyList()
)

data class AccessibilitySnapshot(
    val packageName: String?,
    val eventType: Int,
    val windowClassName: String?,
    val nodeCount: Int,
    val clickableNodeCount: Int,
    val observedAtEpochMillis: Long,
    val structuralNodes: List<StructuralNodeMetadata> = emptyList(),
    val structuralTransition: StructuralTransitionDiagnostics? = null,
    val temporalPattern: TemporalPatternDiagnostics? = null,
    val actionableNodes: ActionableNodeDiagnostics? = null
)

class AccessibilitySnapshotPolicy {
    fun create(
        packageName: String?,
        eventType: Int,
        windowClassName: CharSequence?,
        nodeCount: Int,
        clickableNodeCount: Int,
        observedAtEpochMillis: Long = System.currentTimeMillis(),
        structuralNodes: List<StructuralNodeMetadata> = emptyList()
    ): AccessibilitySnapshot {
        val safeNodeCount = nodeCount.coerceIn(0, MAX_NODE_COUNT)
        val safeStructuralNodes = structuralNodes
            .take(MAX_NODE_COUNT)
            .map { node ->
                node.copy(
                    viewClassName = node.viewClassName?.trim()?.takeIf(String::isNotEmpty),
                    resourceId = node.resourceId?.trim()?.takeIf(String::isNotEmpty),
                    text = node.text?.trim()?.takeIf(String::isNotEmpty),
                    contentDescription = node.contentDescription?.trim()?.takeIf(String::isNotEmpty),
                    childCount = node.childCount.coerceAtLeast(0)
                )
            }
        return AccessibilitySnapshot(
            packageName = packageName?.trim()?.takeIf(String::isNotEmpty),
            eventType = eventType,
            windowClassName = windowClassName?.toString()?.trim()?.takeIf(String::isNotEmpty),
            nodeCount = safeNodeCount,
            clickableNodeCount = clickableNodeCount.coerceIn(0, safeNodeCount),
            observedAtEpochMillis = observedAtEpochMillis.coerceAtLeast(0L),
            structuralNodes = safeStructuralNodes
        )
    }

    fun diagnosticNodes(snapshot: AccessibilitySnapshot): List<StructuralNodeMetadata> =
        snapshot.structuralNodes.take(MAX_DIAGNOSTIC_NODES)

    private companion object {
        const val MAX_DIAGNOSTIC_NODES = 20
        const val MAX_NODE_COUNT = 200
    }
}
