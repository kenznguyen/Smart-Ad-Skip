package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionableNodeDetectorTest {
    private val detector = ActionableNodeDetector()
    private val now = 1_800_000_000_000L

    @Test
    fun detectsClickableEnabledVisibleNodeWithoutAdCompletionDependency() {
        val current = snapshot(
            now,
            listOf(node("com.game:id/next_ad", clickable = true, enabled = true, visible = true))
        )

        val result = detector.detect(current, now)

        assertEquals(ActionableNodeDetectionStatus.DETECTED, result.status)
        assertEquals(1, result.candidates.size)
        assertEquals("com.game:id/next_ad", result.candidates.single().resourceId)
    }

    @Test
    fun detectsGenericClickableNodesWithoutSemanticResourceIds() {
        val current = snapshot(now, listOf(node(null, clickable = true, enabled = true, visible = true)))

        val result = detector.detect(current, now)

        assertEquals(ActionableNodeDetectionStatus.DETECTED, result.status)
        assertEquals(1, result.candidates.size)
    }

    @Test
    fun returnsAllActionableNodesInsteadOfRequiringOneUniqueCloseButton() {
        val current = snapshot(
            now,
            listOf(
                node("com.game:id/next_ad", clickable = true, enabled = true, visible = true),
                node("com.game:id/close", clickable = true, enabled = true, visible = true)
            )
        )

        val result = detector.detect(current, now)

        assertEquals(2, result.candidates.size)
    }

    @Test
    fun rejectsNonClickableNodes() {
        val result = detector.detect(snapshot(now, listOf(node("id", false, true, true))), now)
        assertEquals(ActionableNodeDetectionStatus.NO_ACTIONABLE_NODES, result.status)
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun rejectsDisabledNodes() {
        val result = detector.detect(snapshot(now, listOf(node("id", true, false, true))), now)
        assertEquals(ActionableNodeDetectionStatus.NO_ACTIONABLE_NODES, result.status)
    }

    @Test
    fun rejectsInvisibleNodes() {
        val result = detector.detect(snapshot(now, listOf(node("id", true, true, false))), now)
        assertEquals(ActionableNodeDetectionStatus.NO_ACTIONABLE_NODES, result.status)
    }

    @Test
    fun rejectsNodesWithoutValidBounds() {
        val invalid = node("id", true, true, true).copy(bounds = ScreenBounds(10, 10, 10, 20))
        val result = detector.detect(snapshot(now, listOf(invalid)), now)
        assertEquals(ActionableNodeDetectionStatus.NO_ACTIONABLE_NODES, result.status)
    }

    @Test
    fun rejectsStaleSnapshot() {
        val result = detector.detect(
            snapshot(now - 16_000L, listOf(node("id", true, true, true))),
            now
        )
        assertEquals(ActionableNodeDetectionStatus.STALE_SNAPSHOT, result.status)
        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun rejectsWrongPackage() {
        val result = detector.detect(
            snapshot(now, listOf(node("id", true, true, true)), "com.other.app"),
            now
        )
        assertEquals(ActionableNodeDetectionStatus.WRONG_PACKAGE, result.status)
    }

    private fun snapshot(
        timestamp: Long,
        nodes: List<StructuralNodeMetadata>,
        packageName: String = "com.funnyvideo.cgame.fruitblast"
    ) = AccessibilitySnapshot(
        packageName = packageName,
        eventType = 32,
        windowClassName = "com.funnyvideo.cgame.fruitblast.GameActivity",
        nodeCount = nodes.size,
        clickableNodeCount = nodes.count { it.clickable },
        observedAtEpochMillis = timestamp,
        structuralNodes = nodes
    )

    private fun node(
        resourceId: String?,
        clickable: Boolean,
        enabled: Boolean,
        visible: Boolean
    ) = StructuralNodeMetadata(
        viewClassName = "android.widget.ImageButton",
        resourceId = resourceId,
        clickable = clickable,
        bounds = ScreenBounds(10, 20, 60, 70),
        enabled = enabled,
        visible = visible
    )
}
