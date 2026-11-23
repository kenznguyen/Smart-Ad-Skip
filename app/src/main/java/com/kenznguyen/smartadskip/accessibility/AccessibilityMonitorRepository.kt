package com.kenznguyen.smartadskip.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccessibilityMonitorRepository {
    private val mutableIsConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = mutableIsConnected.asStateFlow()

    private val mutableLatestSnapshot = MutableStateFlow<AccessibilitySnapshot?>(null)
    val latestSnapshot: StateFlow<AccessibilitySnapshot?> = mutableLatestSnapshot.asStateFlow()

    fun setConnected(connected: Boolean) {
        mutableIsConnected.value = connected
        if (!connected) mutableLatestSnapshot.value = null
    }

    fun publish(snapshot: AccessibilitySnapshot) {
        if (mutableIsConnected.value) {
            mutableLatestSnapshot.value = snapshot
        }
    }
}
