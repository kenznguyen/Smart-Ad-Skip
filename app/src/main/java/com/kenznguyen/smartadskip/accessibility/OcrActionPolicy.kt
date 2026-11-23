package com.kenznguyen.smartadskip.accessibility

/**
 * OCR is a secondary text signal only. It can veto a protected or conflicting label,
 * but it can never promote an UNKNOWN visual detection into an actionable control.
 */
enum class OcrActionHint { NEXT_AD, CLOSE, PROTECTED, NO_TEXT, AMBIGUOUS }

class OcrActionPolicy {
    fun analyze(recognizedText: String): OcrActionHint {
        val normalized = recognizedText
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.isEmpty()) return OcrActionHint.NO_TEXT

        val tokens = normalized.split(Regex("\\s+")).toSet()
        val protectedTokens = setOf(
            "install", "download", "buy", "purchase", "claim", "collect",
            "reward", "shop", "sponsored", "continue", "open", "visit", "subscribe"
        )
        val protectedPhrases = listOf("play now", "learn more", "get started", "get it", "sign up", "watch now")
        if (tokens.any { it in protectedTokens } ||
            protectedPhrases.any { normalized.contains(it) }
        ) return OcrActionHint.PROTECTED

        if (tokens.any { it == "next" || it == "skip" }) return OcrActionHint.NEXT_AD
        if (tokens.any { it == "close" || it == "dismiss" } || normalized == "x") return OcrActionHint.CLOSE
        return OcrActionHint.AMBIGUOUS
    }

    fun canProceed(visualAction: VisualActionType, ocrHint: OcrActionHint): Boolean {
        if (visualAction !in APPROVED_VISUAL_ACTIONS) return false
        return when (ocrHint) {
            OcrActionHint.PROTECTED -> false
            OcrActionHint.NEXT_AD -> visualAction == VisualActionType.NEXT_AD
            OcrActionHint.CLOSE -> visualAction == VisualActionType.CLOSE
            // Symbol-only controls remain supported when OCR returns no text.
            OcrActionHint.NO_TEXT -> true
            // OCR returned text, but could not classify it safely.
            OcrActionHint.AMBIGUOUS -> false
        }
    }

    private companion object {
        val APPROVED_VISUAL_ACTIONS = setOf(
            VisualActionType.NEXT_AD,
            VisualActionType.CLOSE
        )
    }
}
