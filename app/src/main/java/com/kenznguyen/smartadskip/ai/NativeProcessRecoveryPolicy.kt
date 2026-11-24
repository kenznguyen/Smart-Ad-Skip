package com.kenznguyen.smartadskip.ai

/**
 * Pure watchdog policy. A timed-out caller is not proof that JNI stopped.
 * Recovery is allowed only by terminating the process that owns the native call.
 */
internal object NativeProcessRecoveryPolicy {
    enum class Action { KEEP_RUNNING, TERMINATE_PROCESS, RESTART_PROCESS, FAIL_CLOSED }

    const val DEFAULT_STUCK_AFTER_MILLIS = 30_000L
    const val MAX_RESTARTS_PER_WINDOW = 2
    const val RESTART_COOLDOWN_MILLIS = 60_000L

    fun evaluate(
        processAlive: Boolean,
        inferenceAgeMillis: Long,
        restartCount: Int,
        nowMillis: Long,
        lastRestartAtMillis: Long?,
        stuckAfterMillis: Long = DEFAULT_STUCK_AFTER_MILLIS,
        maxRestarts: Int = MAX_RESTARTS_PER_WINDOW,
        restartCooldownMillis: Long = RESTART_COOLDOWN_MILLIS
    ): Action {
        if (processAlive) {
            return if (inferenceAgeMillis >= stuckAfterMillis) Action.TERMINATE_PROCESS
            else Action.KEEP_RUNNING
        }
        if (restartCount >= maxRestarts) return Action.FAIL_CLOSED
        if (lastRestartAtMillis != null &&
            nowMillis - lastRestartAtMillis < restartCooldownMillis
        ) return Action.FAIL_CLOSED
        return Action.RESTART_PROCESS
    }
}
