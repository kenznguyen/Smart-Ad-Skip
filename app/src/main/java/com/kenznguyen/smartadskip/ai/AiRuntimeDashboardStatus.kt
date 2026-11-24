package com.kenznguyen.smartadskip.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class QwenRuntimePhase { NOT_IMPORTED, LOADING, READY, FAILED }

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
    val actionCount: Long = 0L
)

/** Process-local, read-only telemetry for the dashboard. It never grants click permission. */
object AiRuntimeDashboardStatus {
    private val mutableState = MutableStateFlow(AiRuntimeDashboardSnapshot())
    val state: StateFlow<AiRuntimeDashboardSnapshot> = mutableState.asStateFlow()

    fun setNotImported() {
        mutableState.value = mutableState.value.copy(
            phase = QwenRuntimePhase.NOT_IMPORTED,
            detail = "model_not_imported"
        )
    }

    fun setLoading(config: Qwen3VlRuntimeConfig = Qwen3VlRuntimeConfig()) {
        mutableState.value = mutableState.value.copy(
            phase = QwenRuntimePhase.LOADING,
            detail = "model_loading",
            config = config
        )
    }

    fun setReady() {
        mutableState.value = mutableState.value.copy(
            phase = QwenRuntimePhase.READY,
            detail = "qwen3_vl_ready"
        )
    }

    fun setFailed(reason: String) {
        mutableState.value = mutableState.value.copy(
            phase = QwenRuntimePhase.FAILED,
            detail = reason.take(120)
        )
    }

    fun recordDecision(
        action: String,
        reason: String,
        frameId: Long,
        observedAtEpochMillis: Long
    ) {
        val previous = mutableState.value
        mutableState.value = previous.copy(
            lastAction = action,
            lastReason = reason.take(120),
            lastFrameId = frameId,
            lastObservedAtEpochMillis = observedAtEpochMillis,
            inferenceCount = previous.inferenceCount + 1,
            waitCount = previous.waitCount + if (action == "WAIT") 1 else 0,
            actionCount = previous.actionCount + if (action == "NEXT_AD" || action == "CLOSE") 1 else 0
        )
    }

    internal fun reset() {
        mutableState.value = AiRuntimeDashboardSnapshot()
    }
}
