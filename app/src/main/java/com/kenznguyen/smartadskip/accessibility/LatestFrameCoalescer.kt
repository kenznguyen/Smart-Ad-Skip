package com.kenznguyen.smartadskip.accessibility

/**
 * A bounded one-slot mailbox for frames observed while screenshot/inference work is busy.
 * New observations replace stale pending work; no frame queue can grow without bound.
 */
internal class LatestFrameCoalescer<T> {
    private var latest: T? = null

    @Synchronized
    fun offer(value: T) {
        latest = value
    }

    @Synchronized
    fun poll(): T? {
        val value = latest
        latest = null
        return value
    }

    @Synchronized
    fun clear() {
        latest = null
    }

    @Synchronized
    fun hasPending(): Boolean = latest != null
}
