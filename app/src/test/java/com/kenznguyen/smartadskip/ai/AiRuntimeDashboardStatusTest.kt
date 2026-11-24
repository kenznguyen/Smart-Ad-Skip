package com.kenznguyen.smartadskip.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiRuntimeDashboardStatusTest {
    @Test fun loadingAndReadyTransitionsAreVisibleToDashboard() {
        AiRuntimeDashboardStatus.reset()
        AiRuntimeDashboardStatus.setLoading()
        assertEquals(QwenRuntimePhase.LOADING, AiRuntimeDashboardStatus.state.value.phase)

        AiRuntimeDashboardStatus.setReady()
        assertEquals(QwenRuntimePhase.READY, AiRuntimeDashboardStatus.state.value.phase)
    }

    @Test fun stoppedRuntimeIsNotReportedAsReady() {
        AiRuntimeDashboardStatus.reset()
        AiRuntimeDashboardStatus.setReady()
        AiRuntimeDashboardStatus.setStopped()
        assertEquals(QwenRuntimePhase.STOPPED, AiRuntimeDashboardStatus.state.value.phase)
    }

    @Test fun failuresAndLastDecisionAreReportedWithoutAuthorizingActions() {
        AiRuntimeDashboardStatus.reset()
        AiRuntimeDashboardStatus.setFailed("native_load_failed")
        assertEquals(QwenRuntimePhase.FAILED, AiRuntimeDashboardStatus.state.value.phase)

        AiRuntimeDashboardStatus.recordDecision(
            action = "WAIT",
            reason = "inference_timeout",
            frameId = 42L,
            observedAtEpochMillis = 1234L
        )
        val state = AiRuntimeDashboardStatus.state.value
        assertEquals("WAIT", state.lastAction)
        assertEquals("inference_timeout", state.lastReason)
        assertEquals(42L, state.lastFrameId)
        assertEquals(1L, state.waitCount)
    }
}
