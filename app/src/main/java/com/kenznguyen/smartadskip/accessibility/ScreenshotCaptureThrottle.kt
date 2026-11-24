package com.kenznguyen.smartadskip.accessibility

/**
 * Enforces Android's minimum interval between screenshot requests.
 *
 * Call only after acquiring the single-flight lifecycle token. A rejected request
 * does not advance the window, so event bursts cannot starve the next eligible frame.
 */
internal class ScreenshotCaptureThrottle(
    private val minIntervalMillis: Long = MIN_INTERVAL_MILLIS
) {
    private var lastRequestAtMillis: Long? = null

    init {
        require(minIntervalMillis > 0L) { "Screenshot interval must be positive" }
    }

    @Synchronized
    fun tryAcquire(nowMillis: Long): Boolean {
        val lastRequest = lastRequestAtMillis
        if (lastRequest != null && nowMillis >= lastRequest &&
            nowMillis - lastRequest < minIntervalMillis
        ) {
            return false
        }
        lastRequestAtMillis = nowMillis
        return true
    }

    /**
     * Rolls back only the reservation created by a request that failed synchronously
     * before Android accepted it. Callback failures must not call this method.
     */
    @Synchronized
    fun release(requestAtMillis: Long) {
        if (lastRequestAtMillis == requestAtMillis) {
            lastRequestAtMillis = null
        }
    }

    @Synchronized
    fun millisUntilAllowed(nowMillis: Long): Long {
        val lastRequest = lastRequestAtMillis ?: return 0L
        if (nowMillis < lastRequest) return 0L
        return (minIntervalMillis - (nowMillis - lastRequest)).coerceAtLeast(0L)
    }

    @Synchronized
    fun reset() {
        lastRequestAtMillis = null
    }

    companion object {
        const val MIN_INTERVAL_MILLIS = 1_000L
    }
}
