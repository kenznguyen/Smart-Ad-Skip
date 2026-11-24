package com.kenznguyen.smartadskip.ai

import com.kenznguyen.smartadskip.accessibility.StructuralNodeMetadata

/**
 * Execution-only veto. Empty labels are denied; symbol-only controls are allowed only
 * for the matching action. This policy never selects an action.
 */
class AutonomousTargetSafetyPolicy {
    fun canExecute(target: StructuralNodeMetadata, action: AdAction): Boolean {
        val rawLabels = listOfNotNull(target.text, target.contentDescription)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (rawLabels.isEmpty()) return false

        val normalized = rawLabels.joinToString(" ")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.isNotEmpty()) {
            val tokens = normalized.split(Regex("\\s+")).toSet()
            if (tokens.any { it in PROTECTED_TOKENS } ||
                PROTECTED_PHRASES.any { phrase -> normalized.contains(phrase) }
            ) return false
        }

        val compactLabels = rawLabels.map { it.lowercase().replace(Regex("\\s+"), "") }
        return when (action) {
            AdAction.CLOSE -> compactLabels.any { it in CLOSE_LABELS || it in CLOSE_SYMBOLS }
            AdAction.NEXT_AD -> compactLabels.any { it in NEXT_LABELS || it in NEXT_SYMBOLS }
            AdAction.WAIT -> false
        }
    }

    private companion object {
        val PROTECTED_TOKENS = setOf(
            "install", "download", "buy", "purchase", "claim", "collect",
            "reward", "shop", "sponsored", "subscribe", "signup", "continue", "open", "visit"
        )
        val PROTECTED_PHRASES = listOf(
            "play now", "learn more", "get started", "get it", "sign up",
            "watch now", "visit site", "open link"
        )
        val CLOSE_LABELS = setOf("close", "dismiss", "exit")
        val CLOSE_SYMBOLS = setOf("x", "×", "✕", "✖")
        val NEXT_LABELS = setOf("skip", "next", "next ad")
        val NEXT_SYMBOLS = setOf(">>", "»", "›", "⏭")
    }
}
