package com.kenznguyen.smartadskip.accessibility

/**
 * Tracks one in-flight screenshot request and invalidates callbacks after service reset.
 */
internal class ScreenshotCaptureLifecycle {
    private var generation = 0L
    private var activeRequest: Long? = null

    @Synchronized
    fun begin(): Long? {
        if (activeRequest != null) return null
        generation += 1
        activeRequest = generation
        return generation
    }

    @Synchronized
    fun isCurrent(requestId: Long): Boolean = activeRequest == requestId

    @Synchronized
    fun finish(requestId: Long) {
        if (activeRequest == requestId) activeRequest = null
    }

    @Synchronized
    fun reset() {
        generation += 1
        activeRequest = null
    }
}
