package com.kenznguyen.smartadskip.overlay

internal object OverlayVisibilityPolicy {
    fun shouldShow(serviceConnected: Boolean, explicitlyRequested: Boolean): Boolean =
        serviceConnected && explicitlyRequested
}
