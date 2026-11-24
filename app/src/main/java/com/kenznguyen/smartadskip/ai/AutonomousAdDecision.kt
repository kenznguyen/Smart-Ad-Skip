package com.kenznguyen.smartadskip.ai

import com.kenznguyen.smartadskip.accessibility.ScreenBounds

/**
 * The model is the sole source of action decisions. The executor only translates a
 * validated decision into an Android Accessibility action; it does not re-rank candidates.
 */
enum class AdAction { NEXT_AD, CLOSE, WAIT }

data class AdUiElement(
    val id: String,
    val bounds: ScreenBounds,
    val description: String? = null,
    val clickable: Boolean = false
)

data class AdFrameContext(
    val packageName: String,
    val observedAtEpochMillis: Long,
    val screenWidth: Int,
    val screenHeight: Int,
    val imageJpeg: ByteArray,
    val elements: List<AdUiElement>
)

data class Qwen3VlOutput(
    val action: AdAction,
    val targetElementId: String? = null,
    val confidence: Float,
    val rationale: String? = null
)

sealed interface AutonomousDecision {
    data class Act(
        val action: AdAction,
        val target: AdUiElement,
        val confidence: Float,
        val rationale: String?
    ) : AutonomousDecision

    data class Wait(val reason: String) : AutonomousDecision
}

interface Qwen3VlRuntime {
    val isModelReady: Boolean
    suspend fun infer(context: AdFrameContext): Qwen3VlOutput
}

/**
 * No rule-based fallback is allowed: if the local model is unavailable, the system waits.
 * The guards here validate the model's output and frame freshness; they do not choose an
 * action on the model's behalf.
 */
class AutonomousAdDecisionEngine(
    private val runtime: Qwen3VlRuntime,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minimumConfidence: Float = DEFAULT_MINIMUM_CONFIDENCE
) {
    suspend fun decide(context: AdFrameContext): AutonomousDecision {
        if (context.packageName != TARGET_PACKAGE) return AutonomousDecision.Wait("wrong_package")
        if (context.screenWidth <= 0 || context.screenHeight <= 0 || context.imageJpeg.isEmpty()) {
            return AutonomousDecision.Wait("invalid_frame")
        }
        if (context.observedAtEpochMillis <= 0L ||
            clock() - context.observedAtEpochMillis !in 0L..MAX_FRAME_AGE_MILLIS
        ) return AutonomousDecision.Wait("stale_frame")
        if (!runtime.isModelReady) return AutonomousDecision.Wait("qwen3_vl_not_ready")

        val output = runCatching { runtime.infer(context) }
            .getOrElse { return AutonomousDecision.Wait("inference_failed") }

        if (output.action == AdAction.WAIT) return AutonomousDecision.Wait("model_wait")
        if (!output.confidence.isFinite() ||
            output.confidence !in minimumConfidence..1.0f
        ) return AutonomousDecision.Wait("confidence_below_threshold")

        val targetId = output.targetElementId ?: return AutonomousDecision.Wait("missing_target")
        val target = context.elements.singleOrNull { it.id == targetId }
            ?: return AutonomousDecision.Wait("target_not_unique")
        if (!target.clickable || !target.bounds.hasArea ||
            target.bounds.left < 0 || target.bounds.top < 0 ||
            target.bounds.right > context.screenWidth ||
            target.bounds.bottom > context.screenHeight
        ) return AutonomousDecision.Wait("target_not_actionable")

        return AutonomousDecision.Act(
            action = output.action,
            target = target,
            confidence = output.confidence,
            rationale = output.rationale?.take(MAX_RATIONALE_LENGTH)
        )
    }

    companion object {
        const val TARGET_PACKAGE = "com.funnyvideo.cgame.fruitblast"
        const val DEFAULT_MINIMUM_CONFIDENCE = 0.85f
        const val MAX_FRAME_AGE_MILLIS = 1_500L
        const val MAX_RATIONALE_LENGTH = 240
    }
}
