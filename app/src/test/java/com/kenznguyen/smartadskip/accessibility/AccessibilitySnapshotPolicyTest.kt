package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySnapshotPolicyTest {
    private val policy = AccessibilitySnapshotPolicy()

    @Test
    fun create_preservesEventTimestamp() {
        val snapshot = policy.create(
            packageName = "com.example.game",
            eventType = 32,
            windowClassName = "android.widget.FrameLayout",
            nodeCount = 12,
            clickableNodeCount = 3,
            observedAtEpochMillis = 1_800_000_000_000L
        )

        assertEquals(1_800_000_000_000L, snapshot.observedAtEpochMillis)
    }

    @Test
    fun create_preservesSafeWindowMetadata() {
        val snapshot = policy.create(
            packageName = "com.example.game",
            eventType = 32,
            windowClassName = "android.widget.FrameLayout",
            nodeCount = 12,
            clickableNodeCount = 3
        )

        assertEquals("com.example.game", snapshot.packageName)
        assertEquals(32, snapshot.eventType)
        assertEquals("android.widget.FrameLayout", snapshot.windowClassName)
        assertEquals(12, snapshot.nodeCount)
        assertEquals(3, snapshot.clickableNodeCount)
    }

    @Test
    fun create_dropsBlankPackageAndWindowClassNames() {
        val snapshot = policy.create(
            packageName = "  ",
            eventType = 0,
            windowClassName = " ",
            nodeCount = 1,
            clickableNodeCount = 1
        )

        assertNull(snapshot.packageName)
        assertNull(snapshot.windowClassName)
    }

    @Test
    fun create_clampsCountsAndNeverReportsMoreClickableNodesThanNodes() {
        val snapshot = policy.create(
            packageName = "com.example.game",
            eventType = 1,
            windowClassName = null,
            nodeCount = 10_000,
            clickableNodeCount = 9_000
        )

        assertEquals(200, snapshot.nodeCount)
        assertEquals(200, snapshot.clickableNodeCount)
    }

    @Test
    fun create_rejectsNegativeCounts() {
        val snapshot = policy.create(
            packageName = "com.example.game",
            eventType = 1,
            windowClassName = null,
            nodeCount = -4,
            clickableNodeCount = -2
        )

        assertEquals(0, snapshot.nodeCount)
        assertEquals(0, snapshot.clickableNodeCount)
    }

    @Test
    fun create_neverExposesUiTextOrInputValues() {
        val snapshot = policy.create(
            packageName = "com.example.game",
            eventType = 1,
            windowClassName = "android.widget.EditText",
            nodeCount = 2,
            clickableNodeCount = 1
        )
        val fieldNames = snapshot::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()

        assertTrue(fieldNames.containsAll(
            setOf("packageName", "eventType", "windowClassName", "nodeCount", "clickableNodeCount")
        ))
        assertFalse(fieldNames.any {
            it.contains("text", ignoreCase = true) ||
                it.contains("input", ignoreCase = true) ||
                it.contains("contentDescription", ignoreCase = true)
        })
    }
}
