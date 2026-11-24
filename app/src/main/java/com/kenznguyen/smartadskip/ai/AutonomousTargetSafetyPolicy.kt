package com.kenznguyen.smartadskip.ai

import com.kenznguyen.smartadskip.accessibility.StructuralNodeMetadata

/**
 * Execution-only veto for clearly protected destinations. It never chooses an action;
 * the model remains the sole selector of NEXT_AD, CLOSE, or WAIT.
 */
class AutonomousTargetSafetyPolicy {
    fun canExecute(target: StructuralNodeMetadata): Boolean {
        val normalized = listOfNotNull(target.text, target.contentDescription)
            .joinToString(" ")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        if (normalized.isEmpty()) return true

        val tokens = normalized.split(Regex("\\s+")).toSet()
        return tokens.none { it in PROTECTED_TOKENS } &&
            PROTECTED_PHRASES.none(normalized::contains)
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
    }
}
