package com.kenznguyen.smartadskip.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeProcessRecoveryPolicyTest {
    @Test fun healthyProcessIsNeverRestarted() {
        val result = NativeProcessRecoveryPolicy.evaluate(
            processAlive = true, inferenceAgeMillis = 5_000L, restartCount = 0,
            nowMillis = 100_000L, lastRestartAtMillis = null
        )
        assertEquals(NativeProcessRecoveryPolicy.Action.KEEP_RUNNING, result)
    }

    @Test fun stuckInferenceRequiresProcessTerminationNotSlotRelease() {
        val result = NativeProcessRecoveryPolicy.evaluate(
            processAlive = true, inferenceAgeMillis = 31_000L, restartCount = 0,
            nowMillis = 100_000L, lastRestartAtMillis = null
        )
        assertEquals(NativeProcessRecoveryPolicy.Action.TERMINATE_PROCESS, result)
    }

    @Test fun deadProcessCanRestartOnlyWithinBudgetAndCooldown() {
        assertEquals(
            NativeProcessRecoveryPolicy.Action.RESTART_PROCESS,
            NativeProcessRecoveryPolicy.evaluate(false, 0L, 0, 100_000L, null)
        )
        assertEquals(
            NativeProcessRecoveryPolicy.Action.FAIL_CLOSED,
            NativeProcessRecoveryPolicy.evaluate(false, 0L, 2, 100_000L, null)
        )
        assertEquals(
            NativeProcessRecoveryPolicy.Action.FAIL_CLOSED,
            NativeProcessRecoveryPolicy.evaluate(false, 0L, 1, 20_000L, 10_000L)
        )
    }

    @Test fun bindingDeathAllowsExplicitUnbindAndRebindWithinRecoveryBudget() {
        assertEquals(
            NativeProcessRecoveryPolicy.Action.RESTART_PROCESS,
            NativeProcessRecoveryPolicy.evaluate(false, 0L, 0, 100_000L, null)
        )
    }

    @Test fun bindingDeathFailsClosedWhenRestartBudgetOrCooldownIsExhausted() {
        assertEquals(
            NativeProcessRecoveryPolicy.Action.FAIL_CLOSED,
            NativeProcessRecoveryPolicy.evaluate(false, 0L, 2, 100_000L, null)
        )
        assertEquals(
            NativeProcessRecoveryPolicy.Action.FAIL_CLOSED,
            NativeProcessRecoveryPolicy.evaluate(false, 0L, 1, 20_000L, 10_000L)
        )
    }

    @Test fun liveProcessBelowThresholdIsNotKilledEvenAfterCallerTimeout() {
        assertEquals(
            NativeProcessRecoveryPolicy.Action.KEEP_RUNNING,
            NativeProcessRecoveryPolicy.evaluate(true, 29_999L, 1, 100_000L, 50_000L)
        )
    }
}
