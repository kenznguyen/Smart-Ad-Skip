package com.kenznguyen.smartadskip.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class QwenRuntimePhase { NOT_IMPORTED, LOADING, READY, FAILED, STOPPED }
enum class InferenceDiagnosticState { IDLE, RUNNING, STUCK }

data class AiRuntimeDashboardSnapshot(
    val phase: QwenRuntimePhase = QwenRuntimePhase.NOT_IMPORTED,
    val detail: String = "model_not_imported",
    val config: Qwen3VlRuntimeConfig = Qwen3VlRuntimeConfig(),
    val lastAction: String = "—",
    val lastReason: String = "—",
    val lastFrameId: Long = 0L,
    val lastObservedAtEpochMillis: Long = 0L,
    val inferenceCount: Long = 0L,
    val waitCount: Long = 0L,
    val actionCount: Long = 0L,
    val inferenceState: InferenceDiagnosticState = InferenceDiagnosticState.IDLE,
    val inferenceStartedCount: Long = 0L,
    val inferenceCompletedCount: Long = 0L,
    val inferenceFailedCount: Long = 0L,
    val inferenceTimeoutCount: Long = 0L,
    val inferenceOverlapCount: Long = 0L,
    val lastInferenceDurationMillis: Long = 0L,
    val maxInferenceDurationMillis: Long = 0L,
    val queueCoalescedCount: Long = 0L,
    val queueReplacementCount: Long = 0L,
    val queueDispatchedCount: Long = 0L,
    val queueDroppedCount: Long = 0L,
    val lastQueueDropReason: String = "—",
    val screenshotRequestedCount: Long = 0L,
    val screenshotSucceededCount: Long = 0L,
    val screenshotFailedCount: Long = 0L,
    val lastScreenshotFailure: String = "—",
    val failClosedCount: Long = 0L,
    val lastFailClosedReason: String = "—",
    val nativeProcessState: String = "NOT_LOADED"
)

/**
 * Read-only process-local diagnostics. Updates never feed back into inference,
 * runtime lifecycle, queue admission, screenshot scheduling, or action authorization.
 */
object AiRuntimeDashboardStatus {
    private val mutableState = MutableStateFlow(AiRuntimeDashboardSnapshot())
    val state: StateFlow<AiRuntimeDashboardSnapshot> = mutableState.asStateFlow()
    private var inferenceStartedAtMillis: Long? = null

    @Synchronized
    private inline fun update(transform: (AiRuntimeDashboardSnapshot) -> AiRuntimeDashboardSnapshot) {
        mutableState.value = transform(mutableState.value)
    }

    fun setNotImported() = update {
        it.copy(phase = QwenRuntimePhase.NOT_IMPORTED, detail = "model_not_imported", nativeProcessState = "NOT_LOADED")
    }

    fun setLoading(config: Qwen3VlRuntimeConfig = Qwen3VlRuntimeConfig()) = update {
        it.copy(phase = QwenRuntimePhase.LOADING, detail = "model_loading", config = config, nativeProcessState = "LOADING")
    }

    fun setReady() = update {
        it.copy(phase = QwenRuntimePhase.READY, detail = "qwen3_vl_ready", nativeProcessState = "READY")
    }

    fun setStopped() = update {
        it.copy(phase = QwenRuntimePhase.STOPPED, detail = "accessibility_service_disconnected", nativeProcessState = "STOPPED")
    }

    fun setFailed(reason: String) = update {
        it.copy(phase = QwenRuntimePhase.FAILED, detail = reason.take(120), nativeProcessState = "FAILED")
    }

    fun setNativeProcessState(value: String) = update {
        it.copy(nativeProcessState = value.take(48))
    }

    @Synchronized
    fun inferenceStarted(nowMillis: Long) {
        update { previous ->
            if (previous.inferenceState == InferenceDiagnosticState.RUNNING ||
                previous.inferenceState == InferenceDiagnosticState.STUCK
            ) {
                previous.copy(inferenceOverlapCount = previous.inferenceOverlapCount + 1)
            } else {
                inferenceStartedAtMillis = nowMillis
                previous.copy(
                    inferenceState = InferenceDiagnosticState.RUNNING,
                    inferenceStartedCount = previous.inferenceStartedCount + 1
                )
            }
        }
    }

    @Synchronized
    fun inferenceCompleted(nowMillis: Long) {
        val startedAt = inferenceStartedAtMillis
        val duration = if (startedAt == null) 0L else (nowMillis - startedAt).coerceAtLeast(0L)
        inferenceStartedAtMillis = null
        update {
            it.copy(
                inferenceState = InferenceDiagnosticState.IDLE,
                inferenceCompletedCount = it.inferenceCompletedCount + 1,
                lastInferenceDurationMillis = duration,
                maxInferenceDurationMillis = maxOf(it.maxInferenceDurationMillis, duration)
            )
        }
    }

    @Synchronized
    fun inferenceFailed(nowMillis: Long, reason: String) {
        val startedAt = inferenceStartedAtMillis
        val duration = if (startedAt == null) 0L else (nowMillis - startedAt).coerceAtLeast(0L)
        inferenceStartedAtMillis = null
        update {
            it.copy(
                inferenceState = InferenceDiagnosticState.IDLE,
                inferenceFailedCount = it.inferenceFailedCount + 1,
                lastInferenceDurationMillis = duration,
                maxInferenceDurationMillis = maxOf(it.maxInferenceDurationMillis, duration),
                lastFailClosedReason = reason.take(120)
            )
        }
    }

    @Synchronized
    fun inferenceTimedOut(nowMillis: Long, reason: String) {
        val startedAt = inferenceStartedAtMillis
        val duration = if (startedAt == null) 0L else (nowMillis - startedAt).coerceAtLeast(0L)
        update {
            it.copy(
                inferenceState = InferenceDiagnosticState.STUCK,
                inferenceTimeoutCount = it.inferenceTimeoutCount + 1,
                lastInferenceDurationMillis = duration,
                maxInferenceDurationMillis = maxOf(it.maxInferenceDurationMillis, duration),
                lastFailClosedReason = reason.take(120)
            )
        }
    }

    fun recordQueueCoalesced(replaced: Boolean) = update {
        it.copy(
            queueCoalescedCount = it.queueCoalescedCount + 1,
            queueReplacementCount = it.queueReplacementCount + if (replaced) 1 else 0
        )
    }

    fun recordQueueDispatched() = update {
        it.copy(queueDispatchedCount = it.queueDispatchedCount + 1)
    }

    fun recordQueueDropped(reason: String) = update {
        it.copy(queueDroppedCount = it.queueDroppedCount + 1, lastQueueDropReason = reason.take(120))
    }

    fun recordScreenshotRequested() = update {
        it.copy(screenshotRequestedCount = it.screenshotRequestedCount + 1)
    }

    fun recordScreenshotSucceeded() = update {
        it.copy(screenshotSucceededCount = it.screenshotSucceededCount + 1)
    }

    fun recordScreenshotFailed(reason: String) = update {
        it.copy(screenshotFailedCount = it.screenshotFailedCount + 1, lastScreenshotFailure = reason.take(120))
    }

    fun recordFailClosed(reason: String) = update {
        it.copy(failClosedCount = it.failClosedCount + 1, lastFailClosedReason = reason.take(120))
    }

    fun recordDecision(action: String, reason: String, frameId: Long, observedAtEpochMillis: Long) {
        update { previous ->
            previous.copy(
                lastAction = action,
                lastReason = reason.take(120),
                lastFrameId = frameId,
                lastObservedAtEpochMillis = observedAtEpochMillis,
                inferenceCount = previous.inferenceCount + 1,
                waitCount = previous.waitCount + if (action == "WAIT") 1 else 0,
                actionCount = previous.actionCount + if (action == "NEXT_AD" || action == "CLOSE") 1 else 0
            )
        }
    }

    @Synchronized
    internal fun reset() {
        inferenceStartedAtMillis = null
        mutableState.value = AiRuntimeDashboardSnapshot()
    }
}
