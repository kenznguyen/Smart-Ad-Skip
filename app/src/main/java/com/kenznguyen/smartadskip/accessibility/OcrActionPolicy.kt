package com.kenznguyen.smartadskip.accessibility

/**
 * OCR classifies candidate labels but never grants click permission.
 * Symbol-only controls may use the pixel classifier for candidate discovery only.
 * The final click decision always belongs to live Accessibility + Click Authorization.
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

    /**
     * Resolves a candidate action, not click permission.
     * Recognized text takes precedence. Pixel classification is consulted only for NO_TEXT
     * symbol-only controls such as >> and X, and cannot override a recognized label.
     */
    fun resolveCandidate(
        recognizedText: String,
        symbolDiscovery: VisualActionType = VisualActionType.UNKNOWN
    ): VisualActionType? = when (analyze(recognizedText)) {
        OcrActionHint.NEXT_AD -> VisualActionType.NEXT_AD
        OcrActionHint.CLOSE -> VisualActionType.CLOSE
        OcrActionHint.NO_TEXT -> symbolDiscovery.takeIf { it in APPROVED_ACTIONS }
        OcrActionHint.PROTECTED, OcrActionHint.AMBIGUOUS -> null
    }

    private companion object {
        val APPROVED_ACTIONS = setOf(VisualActionType.NEXT_AD, VisualActionType.CLOSE)
    }
}
