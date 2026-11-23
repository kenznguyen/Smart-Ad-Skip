package com.kenznguyen.smartadskip.accessibility

enum class ActionableControlType {
    NEXT_AD,
    CLOSE,
    CONTINUE,
    PROTECTED,
    UNKNOWN
}

/**
 * Only explicit resource identifiers can authorize a click.
 * Visual appearance, class names, and generic clickability never imply intent.
 */
class ActionableControlPolicy {
    fun classify(resourceId: String?): ActionableControlType {
        if (resourceId.isNullOrBlank()) return ActionableControlType.UNKNOWN

        val normalized = resourceId
            .replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .lowercase()
        val tokens = normalized.split(Regex("[^a-z0-9]+")).filter(String::isNotBlank)

        if (
            tokens.any { it in PROTECTED_TOKENS } ||
            (tokens.contains("ad") && tokens.contains("container"))
        ) return ActionableControlType.PROTECTED

        if (tokens.any { it in NEXT_AD_TOKENS }) return ActionableControlType.NEXT_AD
        if (tokens.any { it in CLOSE_TOKENS }) return ActionableControlType.CLOSE
        if (tokens.any { it in CONTINUE_TOKENS }) return ActionableControlType.CONTINUE
        return ActionableControlType.UNKNOWN
    }

    private companion object {
        val PROTECTED_TOKENS = setOf(
            "adview", "advertisement", "install", "download",
            "claim", "collect", "reward", "prize", "sponsored", "store", "purchase"
        )
        val NEXT_AD_TOKENS = setOf("next_ad", "nextad", "next")
        val CLOSE_TOKENS = setOf("close", "dismiss", "skip")
        val CONTINUE_TOKENS = setOf("continue", "resume")
    }
}
