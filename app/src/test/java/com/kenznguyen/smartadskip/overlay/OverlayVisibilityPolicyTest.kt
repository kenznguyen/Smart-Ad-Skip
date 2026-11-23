package com.kenznguyen.smartadskip.overlay

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
}
