package com.kenznguyen.smartadskip.accessibility

/**
 * Tracks one screenshot request from Android submission through AI completion.
 * A capture callback deadline can retire a request that Android never completes.
 */
internal class ScreenshotCaptureLifecycle {
    private var generation = 0L
    private var activeRequest: Long? = null
    private var capturePending = false

    @Synchronized
    fun begin(): Long? {
        if (activeRequest != null) return null
        generation += 1
        activeRequest = generation
        capturePending = true
        return generation
    }

    /** Claims the one allowed screenshot callback without ending AI ownership. */
    @Synchronized
    fun completeCapture(requestId: Long): Boolean {
        if (activeRequest != requestId || !capturePending) return false
        capturePending = false
        return true
    }

    /** Retires a request only when its Android screenshot callback is still pending. */
    @Synchronized
    fun timeoutCapture(requestId: Long): Boolean = releasePendingCapture(requestId)

    /** Callback failure follows the same pending-only retirement rule as timeout. */
    @Synchronized
    fun failCapture(requestId: Long): Boolean = releasePendingCapture(requestId)

    private fun releasePendingCapture(requestId: Long): Boolean {
        if (activeRequest != requestId || !capturePending) return false
        capturePending = false
        activeRequest = null
        return true
    }

    @Synchronized
    fun isCurrent(requestId: Long): Boolean = activeRequest == requestId

    @Synchronized
    fun finish(requestId: Long) {
        if (activeRequest == requestId) {
            activeRequest = null
            capturePending = false
        }
    }

    @Synchronized
    fun reset() {
        generation += 1
        activeRequest = null
        capturePending = false
    }
}
