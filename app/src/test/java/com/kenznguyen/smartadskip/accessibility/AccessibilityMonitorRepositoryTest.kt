package com.kenznguyen.smartadskip.accessibility

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityMonitorRepositoryTest {
    @After
    fun resetRepository() {
        AccessibilityMonitorRepository.setConnected(false)
    }

    @Test
    fun publish_ignoresSnapshotsWhileDisconnected() {
        AccessibilityMonitorRepository.setConnected(false)
        AccessibilityMonitorRepository.publish(snapshot("com.example.game"))

        assertNull(AccessibilityMonitorRepository.latestSnapshot.value)
    }

    @Test
    fun disconnect_clearsLatestSnapshotAndReconnectAcceptsNewEvents() {
        AccessibilityMonitorRepository.setConnected(true)
        AccessibilityMonitorRepository.publish(snapshot("com.example.first"))
        assertEquals("com.example.first", AccessibilityMonitorRepository.latestSnapshot.value?.packageName)

        AccessibilityMonitorRepository.setConnected(false)
        assertNull(AccessibilityMonitorRepository.latestSnapshot.value)

        AccessibilityMonitorRepository.setConnected(true)
        AccessibilityMonitorRepository.publish(snapshot("com.example.second"))
        assertEquals("com.example.second", AccessibilityMonitorRepository.latestSnapshot.value?.packageName)
        assertTrue(AccessibilityMonitorRepository.isConnected.value)
    }

    private fun snapshot(packageName: String) = AccessibilitySnapshot(
        packageName = packageName,
        eventType = 32,
        windowClassName = null,
        nodeCount = 0,
        clickableNodeCount = 0,
        observedAtEpochMillis = 1_800_000_000_000L
    )
}
