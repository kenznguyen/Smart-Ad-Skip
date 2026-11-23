package com.kenznguyen.smartadskip.accessibility

/** OCR and pixel classification discover CLOSE candidates only; neither authorizes a click. */
enum class OcrActionHint { CLOSE, PROTECTED, NO_TEXT, AMBIGUOUS }

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

        if (tokens.any { it == "close" || it == "dismiss" } || normalized == "x") {
            return OcrActionHint.CLOSE
        }
        return OcrActionHint.AMBIGUOUS
    }

    /** Returns a candidate action only. Final permission remains in Click Authorization. */
    fun resolveCandidate(
        recognizedText: String,
        symbolDiscovery: VisualActionType = VisualActionType.UNKNOWN
    ): VisualActionType? = when (analyze(recognizedText)) {
        OcrActionHint.CLOSE -> VisualActionType.CLOSE
        OcrActionHint.NO_TEXT -> VisualActionType.CLOSE.takeIf { symbolDiscovery == VisualActionType.CLOSE }
        OcrActionHint.PROTECTED, OcrActionHint.AMBIGUOUS -> null
    }
}
