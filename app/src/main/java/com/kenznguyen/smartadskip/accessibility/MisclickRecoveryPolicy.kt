package com.kenznguyen.smartadskip.accessibility

/**
 * Bounds recovery after an authorized click opens the Play Store or a browser.
 *
 * BACK is never sent for an arbitrary external foreground package. The target app
 * must be positively observed and its active application window validated before
 * recovery is considered complete.
 */
internal class MisclickRecoveryPolicy(
    private val maxBackAttempts: Int = DEFAULT_MAX_BACK_ATTEMPTS
) {
    enum class Decision {
        IDLE,
        WAIT,
        TARGET_CONFIRMED,
        REQUEST_BACK,
        EXHAUSTED,
        ABORT_UNVERIFIED
    }

    private var pending = false
    private var backAttemptInFlight = false
    private var backAttempts = 0

    val isPending: Boolean
        get() = pending

    val attempts: Int
        get() = backAttempts

    init {
        require(maxBackAttempts > 0) { "maxBackAttempts must be positive" }
    }

    fun onClickDispatched() {
        pending = true
        backAttemptInFlight = false
        backAttempts = 0
    }

    fun onWindowObserved(
        packageName: String?,
        targetWindowValid: Boolean
    ): Decision {
        if (!pending) return Decision.IDLE

        if (packageName == TARGET_PACKAGE) {
            pending = false
            backAttemptInFlight = false
            return if (targetWindowValid) {
                Decision.TARGET_CONFIRMED
            } else {
                Decision.ABORT_UNVERIFIED
            }
        }

        if (packageName.isNullOrBlank() || packageName !in RECOVERABLE_EXTERNAL_PACKAGES) {
            pending = false
            backAttemptInFlight = false
            return Decision.ABORT_UNVERIFIED
        }

        if (backAttemptInFlight) return Decision.WAIT
        if (backAttempts >= maxBackAttempts) {
            pending = false
            return Decision.EXHAUSTED
        }

        backAttempts += 1
        backAttemptInFlight = true
        return Decision.REQUEST_BACK
    }

    fun onBackAttemptSettled() {
        backAttemptInFlight = false
    }

    fun reset() {
        pending = false
        backAttemptInFlight = false
        backAttempts = 0
    }

    private companion object {
        const val TARGET_PACKAGE = "com.funnyvideo.cgame.fruitblast"
        const val DEFAULT_MAX_BACK_ATTEMPTS = 2
        val RECOVERABLE_EXTERNAL_PACKAGES = setOf(
            "com.android.vending",
            "com.android.chrome",
            "com.android.browser"
        )
    }
}
