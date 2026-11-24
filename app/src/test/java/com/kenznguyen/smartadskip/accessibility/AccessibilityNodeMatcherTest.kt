package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccessibilityNodeMatcherTest {
    private val matcher = AccessibilityNodeMatcher()

    @Test
    fun matchesSameSemanticNodeWhenBoundsChange() {
        val detected = node(
            className = "android.widget.Button",
            text = "Close",
            bounds = ScreenBounds(800, 100, 900, 200)
        )
        val live = listOf(
            node("android.widget.Button", "Close", ScreenBounds(760, 120, 880, 220)),
            node("android.widget.Button", "Install", ScreenBounds(500, 100, 600, 200))
        )

        assertEquals(0, matcher.findUniqueMatch(detected, live))
    }

    @Test
    fun rejectsUnlabelledSymbolOnlyNodeEvenWhenBoundsMatch() {
        val detected = node(
            className = "android.widget.ImageButton",
            text = null,
            bounds = ScreenBounds(800, 100, 900, 200),
            childCount = 1
        )
        val live = listOf(
            node("android.widget.ImageButton", null, ScreenBounds(760, 120, 880, 220), 1),
            node("android.widget.ImageButton", null, ScreenBounds(100, 500, 200, 600), 1)
        )

        assertNull(matcher.findUniqueMatch(detected, live))
    }

    @Test
    fun rejectsAccessibilityChildTreeChangesForUnlabelledControl() {
        val detected = node(
            "android.widget.ImageButton",
            null,
            ScreenBounds(800, 100, 900, 200),
            childCount = 0
        )
        val live = listOf(
            node("android.widget.ImageButton", null, ScreenBounds(760, 120, 880, 220), childCount = 2)
        )

        assertNull(matcher.findUniqueMatch(detected, live))
    }

    @Test
    fun rejectsAmbiguousIdenticalNodesInsteadOfGuessing() {
        val detected = node("android.widget.Button", "Close", ScreenBounds(400, 100, 500, 200))
        val live = listOf(
            node("android.widget.Button", "Close", ScreenBounds(300, 100, 400, 200)),
            node("android.widget.Button", "Close", ScreenBounds(500, 100, 600, 200))
        )

        assertNull(matcher.findUniqueMatch(detected, live))
    }

    @Test
    fun rejectsNodeWhoseLabelChangesBeforeClickEvenWhenBoundsAreIdentical() {
        val detected = node("android.widget.Button", "Close", ScreenBounds(400, 100, 500, 200))
        val live = listOf(node("android.widget.Button", "Install", ScreenBounds(400, 100, 500, 200)))

        assertNull(matcher.findUniqueMatch(detected, live))
    }

    private fun node(
        className: String,
        text: String?,
        bounds: ScreenBounds,
        childCount: Int = 0
    ) = StructuralNodeMetadata(
        viewClassName = className,
        resourceId = null,
        clickable = true,
        bounds = bounds,
        enabled = true,
        visible = true,
        text = text,
        contentDescription = null,
        childCount = childCount
    )
}
