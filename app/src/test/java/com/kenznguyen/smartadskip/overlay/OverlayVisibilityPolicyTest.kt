package com.kenznguyen.smartadskip.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayVisibilityPolicyTest {
    @Test
    fun overlayIsHiddenByDefault() {
        assertFalse(OverlayVisibilityPolicy.shouldShow(false, false))
        assertFalse(OverlayVisibilityPolicy.shouldShow(true, false))
    }

    @Test
    fun overlayRequiresConnectedAccessibilityServiceAndExplicitOptIn() {
        assertFalse(OverlayVisibilityPolicy.shouldShow(false, true))
        assertTrue(OverlayVisibilityPolicy.shouldShow(true, true))
    }

    @Test
    fun positionIsClampedInsideAvailableDisplayArea() {
        assertEquals(0, OverlayVisibilityPolicy.clampPosition(-20, 500))
        assertEquals(280, OverlayVisibilityPolicy.clampPosition(500, 280))
        assertEquals(120, OverlayVisibilityPolicy.clampPosition(120, 280))
    }

    @Test
    fun edgeSnapChoosesNearestHorizontalEdge() {
        assertEquals(OverlayVisibilityPolicy.Edge.LEFT, OverlayVisibilityPolicy.nearestEdge(40, 300))
        assertEquals(OverlayVisibilityPolicy.Edge.RIGHT, OverlayVisibilityPolicy.nearestEdge(260, 300))
    }
}
