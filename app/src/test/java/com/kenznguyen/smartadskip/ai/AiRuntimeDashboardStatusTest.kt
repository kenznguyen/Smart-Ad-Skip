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
        AiRuntimeDashboardStatus.setReady()
        AiRuntimeDashboardStatus.setFailed("native_inference_failed")
        assertEquals(QwenRuntimePhase.FAILED, AiRuntimeDashboardStatus.state.value.phase)
        AiRuntimeDashboardStatus.recordDecision("WAIT", "inference_timeout", 42L, 1234L)
        val state = AiRuntimeDashboardStatus.state.value
        assertEquals("WAIT", state.lastAction)
        assertEquals("inference_timeout", state.lastReason)
        assertEquals(42L, state.lastFrameId)
        assertEquals(1L, state.waitCount)
    }

    @Test fun inferenceCountersAndStateTrackSuccessFailureAndTimeout() {
        AiRuntimeDashboardStatus.reset()
        AiRuntimeDashboardStatus.inferenceStarted(10L)
        AiRuntimeDashboardStatus.inferenceStarted(20L)
        var state = AiRuntimeDashboardStatus.state.value
        assertEquals(InferenceDiagnosticState.RUNNING, state.inferenceState)
        assertEquals(1L, state.inferenceStartedCount)
        assertEquals(1L, state.inferenceOverlapCount)

        AiRuntimeDashboardStatus.inferenceCompleted(35L)
        state = AiRuntimeDashboardStatus.state.value
        assertEquals(InferenceDiagnosticState.IDLE, state.inferenceState)
        assertEquals(1L, state.inferenceCompletedCount)
        assertEquals(25L, state.lastInferenceDurationMillis)

        AiRuntimeDashboardStatus.inferenceStarted(40L)
        AiRuntimeDashboardStatus.inferenceTimedOut(50L, "native_timeout")
        state = AiRuntimeDashboardStatus.state.value
        assertEquals(InferenceDiagnosticState.STUCK, state.inferenceState)
        assertEquals(1L, state.inferenceTimeoutCount)
        assertEquals("native_timeout", state.lastFailClosedReason)
        AiRuntimeDashboardStatus.inferenceFailed(60L, "native_failed")
        state = AiRuntimeDashboardStatus.state.value
        assertEquals(InferenceDiagnosticState.IDLE, state.inferenceState)
        assertEquals(1L, state.inferenceFailedCount)
    }

    @Test fun queueScreenshotAndFailClosedDiagnosticsAreReadOnlyCounters() {
        AiRuntimeDashboardStatus.reset()
        AiRuntimeDashboardStatus.recordQueueCoalesced(replaced = true)
        AiRuntimeDashboardStatus.recordQueueDispatched()
        AiRuntimeDashboardStatus.recordQueueDropped("wrong_foreground")
        AiRuntimeDashboardStatus.recordScreenshotRequested()
        AiRuntimeDashboardStatus.recordScreenshotSucceeded()
        AiRuntimeDashboardStatus.recordScreenshotFailed("capture_timeout")
        AiRuntimeDashboardStatus.recordFailClosed("foreground_window_mismatch")
        AiRuntimeDashboardStatus.setNativeProcessState("JNI_RUNNING")
        val state = AiRuntimeDashboardStatus.state.value
        assertEquals(1L, state.queueCoalescedCount)
        assertEquals(1L, state.queueReplacementCount)
        assertEquals(1L, state.queueDispatchedCount)
        assertEquals(1L, state.queueDroppedCount)
        assertEquals("wrong_foreground", state.lastQueueDropReason)
        assertEquals(1L, state.screenshotRequestedCount)
        assertEquals(1L, state.screenshotSucceededCount)
        assertEquals(1L, state.screenshotFailedCount)
        assertEquals("capture_timeout", state.lastScreenshotFailure)
        assertEquals(1L, state.failClosedCount)
        assertEquals("foreground_window_mismatch", state.lastFailClosedReason)
        assertEquals("JNI_RUNNING", state.nativeProcessState)
    }

    @Test fun resetClearsCountersWithoutChangingDecisionPermission() {
        AiRuntimeDashboardStatus.reset()
        AiRuntimeDashboardStatus.inferenceStarted(1L)
        AiRuntimeDashboardStatus.reset()
        val state = AiRuntimeDashboardStatus.state.value
        assertEquals(0L, state.inferenceStartedCount)
        assertEquals(InferenceDiagnosticState.IDLE, state.inferenceState)
    }
}
