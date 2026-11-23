package com.kenznguyen.smartadskip.overlay

internal object OverlayVisibilityPolicy {
    enum class Edge { LEFT, RIGHT }

    fun shouldShow(serviceConnected: Boolean, explicitlyRequested: Boolean): Boolean =
        serviceConnected && explicitlyRequested

    fun clampPosition(value: Int, maximum: Int): Int =
        value.coerceIn(0, maximum.coerceAtLeast(0))

    fun nearestEdge(x: Int, availableWidth: Int): Edge =
        if (x <= availableWidth / 2) Edge.LEFT else Edge.RIGHT
}
