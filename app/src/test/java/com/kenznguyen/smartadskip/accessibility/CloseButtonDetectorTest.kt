package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloseButtonDetectorTest {
    private val detector = CloseButtonDetector()
    private val now = 1_800_000_000_000L

    @Test
    fun detectsSingleClickableCloseControlOnlyAfterAdCompleted() {
        val previous = snapshot(now - 1_000L, listOf(node("com.game:id/ad_container")))
        val current = snapshot(now, listOf(node("com.game:id/btn_close", clickable = true)))

        val result = detector.detect(current, previous, AdState.AD_COMPLETED, now)

        assertEquals(CloseButtonDetectionStatus.DETECTED, result.status)
        assertEquals("com.game:id/btn_close", result.candidate?.resourceId)
        assertEquals(ScreenBounds(10, 20, 60, 70), result.candidate?.bounds)
    }

    @Test
    fun doesNotDetectCloseControlWhileAdIsStillPlaying() {
        val previous = snapshot(now - 1_000L, listOf(node("com.game:id/ad_container")))
        val current = snapshot(now, listOf(node("com.game:id/btn_close", clickable = true)))

        val result = detector.detect(current, previous, AdState.AD_PLAYING, now)

        assertEquals(CloseButtonDetectionStatus.AD_NOT_COMPLETED, result.status)
        assertNull(result.candidate)
    }

    @Test
    fun doesNotTreatNonClickableCloseViewAsCloseButton() {
        val previous = snapshot(now - 1_000L, listOf(node("com.game:id/ad_container")))
        val current = snapshot(now, listOf(node("com.game:id/btn_close")))

        val result = detector.detect(current, previous, AdState.AD_COMPLETED, now)

        assertEquals(CloseButtonDetectionStatus.NO_UNIQUE_CLOSE_CONTROL, result.status)
        assertNull(result.candidate)
    }

    @Test
    fun doesNotInferCloseButtonFromGenericClickableNode() {
        val previous = snapshot(now - 1_000L, listOf(node("com.game:id/ad_container")))
        val current = snapshot(now, listOf(node("com.game:id/button", clickable = true)))

        val result = detector.detect(current, previous, AdState.AD_COMPLETED, now)

        assertEquals(CloseButtonDetectionStatus.NO_UNIQUE_CLOSE_CONTROL, result.status)
        assertNull(result.candidate)
    }

    @Test
    fun rejectsStaleCurrentSnapshot() {
        val previous = snapshot(now - 2_000L, listOf(node("com.game:id/ad_container")))
        val current = snapshot(now - 16_000L, listOf(node("com.game:id/close", clickable = true)))

        val result = detector.detect(current, previous, AdState.AD_COMPLETED, now)

        assertEquals(CloseButtonDetectionStatus.STALE_SNAPSHOT, result.status)
        assertNull(result.candidate)
    }

    @Test
    fun rejectsWrongPackage() {
        val previous = snapshot(now - 1_000L, listOf(node("com.game:id/ad_container")))
        val current = snapshot(now, listOf(node("com.other:id/close", clickable = true)), "com.other.app")

        val result = detector.detect(current, previous, AdState.AD_COMPLETED, now)

        assertEquals(CloseButtonDetectionStatus.WRONG_PACKAGE, result.status)
        assertNull(result.candidate)
    }

    @Test
    fun rejectsAmbiguousMultipleCloseControls() {
        val previous = snapshot(now - 1_000L, listOf(node("com.game:id/ad_container")))
        val current = snapshot(
            now,
            listOf(
                node("com.game:id/close_button", clickable = true),
                node("com.game:id/ad_dismiss", clickable = true)
            )
        )

        val result = detector.detect(current, previous, AdState.AD_COMPLETED, now)

        assertEquals(CloseButtonDetectionStatus.NO_UNIQUE_CLOSE_CONTROL, result.status)
        assertNull(result.candidate)
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

    private fun node(resourceId: String, clickable: Boolean = false) =
        StructuralNodeMetadata(
            viewClassName = "android.widget.ImageButton",
            resourceId = resourceId,
            clickable = clickable,
            bounds = ScreenBounds(10, 20, 60, 70)
        )
}
