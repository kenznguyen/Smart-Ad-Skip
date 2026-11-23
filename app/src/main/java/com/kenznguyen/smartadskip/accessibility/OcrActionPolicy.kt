package com.kenznguyen.smartadskip.accessibility

/**
 * OCR is a secondary text signal only. It can veto a protected or conflicting label,
 * but it can never promote an UNKNOWN visual detection into an actionable control.
 */
enum class OcrActionHint { NEXT_AD, CLOSE, PROTECTED, UNKNOWN }

class OcrActionPolicy {
    fun analyze(recognizedText: String): OcrActionHint {
        val normalized = recognizedText
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.isEmpty()) return OcrActionHint.UNKNOWN

        val tokens = normalized.split(Regex("\\s+")).toSet()
        val protectedTokens = setOf(
            "install", "download", "buy", "purchase", "claim", "collect",
            "reward", "shop", "sponsored"
        )
        val protectedPhrases = listOf("play now", "learn more", "get started", "get it")
        if (tokens.any { it in protectedTokens } ||
            protectedPhrases.any { normalized.contains(it) }
        ) return OcrActionHint.PROTECTED

        if (tokens.any { it == "next" || it == "skip" }) return OcrActionHint.NEXT_AD
        if (tokens.any { it == "close" || it == "dismiss" }) return OcrActionHint.CLOSE
        return OcrActionHint.UNKNOWN
    }

    fun canProceed(visualAction: VisualActionType, ocrHint: OcrActionHint): Boolean {
        if (visualAction !in APPROVED_VISUAL_ACTIONS) return false
        return when (ocrHint) {
            OcrActionHint.PROTECTED -> false
            OcrActionHint.NEXT_AD -> visualAction == VisualActionType.NEXT_AD
            OcrActionHint.CLOSE -> visualAction == VisualActionType.CLOSE
            // Symbol-only controls remain supported by the visual detector.
            OcrActionHint.UNKNOWN -> true
        }
    }

    private companion object {
        val APPROVED_VISUAL_ACTIONS = setOf(
            VisualActionType.NEXT_AD,
            VisualActionType.CLOSE
        )
    }
}
