package com.kenznguyen.smartadskip.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object OverlayRequestRepository {
    private val mutableRequested = MutableStateFlow(false)
    val requested: StateFlow<Boolean> = mutableRequested.asStateFlow()

    fun setRequested(requested: Boolean) {
        mutableRequested.value = requested
    }
}
