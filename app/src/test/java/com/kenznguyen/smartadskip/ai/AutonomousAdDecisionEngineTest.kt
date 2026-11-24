package com.kenznguyen.smartadskip.ai

import com.kenznguyen.smartadskip.accessibility.ScreenBounds
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousAdDecisionEngineTest {
    private val now = 10_000L
    private val target = AdUiElement("close-1", ScreenBounds(900, 20, 980, 100), "X", true)
    private fun context(
        packageName: String = AutonomousAdDecisionEngine.TARGET_PACKAGE,
        observedAt: Long = now,
        elements: List<AdUiElement> = listOf(target)
    ) = AdFrameContext(packageName, observedAt, 1_000, 2_000, byteArrayOf(1, 2, 3), elements)

    private class FakeRuntime(
        override var isModelReady: Boolean = true,
        var output: Qwen3VlOutput = Qwen3VlOutput(AdAction.CLOSE, "close-1", 0.96f)
    ) : Qwen3VlRuntime {
        override suspend fun infer(context: AdFrameContext) = output
    }

    @Test fun modelChoosesCloseAndEnginePreservesItsDecision() = runBlocking {
        val decision = AutonomousAdDecisionEngine(FakeRuntime(), { now }).decide(context())
        assertTrue(decision is AutonomousDecision.Act)
        assertEquals(AdAction.CLOSE, (decision as AutonomousDecision.Act).action)
    }

    @Test fun modelCanChooseNextAdIndependentlyOfCoreRules() = runBlocking {
        val runtime = FakeRuntime(output = Qwen3VlOutput(AdAction.NEXT_AD, "close-1", 0.91f))
        val decision = AutonomousAdDecisionEngine(runtime, { now }).decide(context())
        assertEquals(AdAction.NEXT_AD, (decision as AutonomousDecision.Act).action)
    }

    @Test fun unavailableLocalModelWaitsWithoutRuleFallback() = runBlocking {
        val decision = AutonomousAdDecisionEngine(FakeRuntime(isModelReady = false), { now }).decide(context())
        assertEquals(AutonomousDecision.Wait("qwen3_vl_not_ready"), decision)
    }

    @Test fun staleFrameIsRejected() = runBlocking {
        val decision = AutonomousAdDecisionEngine(FakeRuntime(), { now }).decide(context(observedAt = now - 2_000))
        assertEquals(AutonomousDecision.Wait("stale_frame"), decision)
    }

    @Test fun wrongPackageIsRejected() = runBlocking {
        val decision = AutonomousAdDecisionEngine(FakeRuntime(), { now }).decide(context(packageName = "other.app"))
        assertEquals(AutonomousDecision.Wait("wrong_package"), decision)
    }

    @Test fun lowConfidenceAndUnknownTargetAreRejected() = runBlocking {
        val low = FakeRuntime(output = Qwen3VlOutput(AdAction.CLOSE, "close-1", 0.5f))
        assertEquals(AutonomousDecision.Wait("confidence_below_threshold"), AutonomousAdDecisionEngine(low, { now }).decide(context()))
        val missing = FakeRuntime(output = Qwen3VlOutput(AdAction.CLOSE, "missing", 0.99f))
        assertEquals(AutonomousDecision.Wait("target_not_unique"), AutonomousAdDecisionEngine(missing, { now }).decide(context()))
    }

    @Test fun duplicateTargetIdsAreRejected() = runBlocking {
        val decision = AutonomousAdDecisionEngine(FakeRuntime(), { now })
            .decide(context(elements = listOf(target, target.copy(bounds = ScreenBounds(1, 1, 10, 10)))))
        assertEquals(AutonomousDecision.Wait("target_not_unique"), decision)
    }

    @Test fun invalidBoundsAreRejected() = runBlocking {
        val invalid = target.copy(bounds = ScreenBounds(-1, 0, 20, 20))
        val decision = AutonomousAdDecisionEngine(FakeRuntime(), { now })
            .decide(context(elements = listOf(invalid)))
        assertEquals(AutonomousDecision.Wait("target_not_actionable"), decision)
    }

    @Test fun modelWaitDecisionIsRespected() = runBlocking {
        val runtime = FakeRuntime(output = Qwen3VlOutput(AdAction.WAIT, confidence = 1f))
        assertEquals(AutonomousDecision.Wait("model_wait"), AutonomousAdDecisionEngine(runtime, { now }).decide(context()))
    }
}
