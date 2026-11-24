package com.kenznguyen.smartadskip.accessibility

/**
 * Coalesces noisy Accessibility observations while allowing lifecycle transitions through
 * immediately. The service stores immutable event metadata, never framework event objects.
 */
internal class AccessibilityEventDebouncer<T>(
    private val debounceMillis: Long
) {
    init {
        require(debounceMillis >= 0L)
    }

    private var pending: T? = null

    @Synchronized
    fun submit(value: T, isCriticalTransition: Boolean): Submission<T> {
        if (isCriticalTransition) {
            pending = null
            return Submission.Immediate(value)
        }
        pending = value
        return Submission.Deferred(debounceMillis)
    }

    @Synchronized
    fun flush(): T? {
        val value = pending
        pending = null
        return value
    }

    @Synchronized
    fun clear() {
        pending = null
    }

    sealed interface Submission<out T> {
        data class Immediate<T>(val value: T) : Submission<T>
        data class Deferred(val delayMillis: Long) : Submission<Nothing>
    }
}
