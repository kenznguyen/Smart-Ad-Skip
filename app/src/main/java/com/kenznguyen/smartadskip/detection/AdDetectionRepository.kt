package com.kenznguyen.smartadskip.detection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdDetectionRepository {
    private val mutableState = MutableStateFlow(AdState.UNKNOWN)
    val state: StateFlow<AdState> = mutableState.asStateFlow()

    fun publish(state: AdState) {
        mutableState.value = state
    }

    fun reset() {
        mutableState.value = AdState.UNKNOWN
    }
}
