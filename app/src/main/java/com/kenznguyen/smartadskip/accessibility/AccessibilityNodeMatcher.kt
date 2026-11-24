package com.kenznguyen.smartadskip.accessibility

/**
 * Re-identifies a live Accessibility node without requiring its screen bounds to remain
 * byte-for-byte identical. Bounds are used only to disambiguate otherwise equivalent nodes.
 */
class AccessibilityNodeMatcher {
    fun findUniqueMatch(
        detected: StructuralNodeMetadata,
        liveNodes: List<StructuralNodeMetadata>
    ): Int? {
        if (!detected.clickable || !detected.enabled || !detected.visible ||
            !detected.bounds.hasArea || detected.viewClassName.isNullOrBlank()
        ) return null

        val detectedLabels = labels(detected)
        if (detectedLabels.isEmpty()) return null
        val candidates = liveNodes.mapIndexedNotNull { index, live ->
            if (!live.clickable || !live.enabled || !live.visible || !live.bounds.hasArea) {
                return@mapIndexedNotNull null
            }
            if (live.viewClassName != detected.viewClassName) return@mapIndexedNotNull null

            val liveLabels = labels(live)
            val identityMatches = liveLabels.isNotEmpty() && detectedLabels == liveLabels
            if (identityMatches) index to centerDistance(detected.bounds, live.bounds) else null
        }.sortedBy { it.second }

        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first().first

        val best = candidates[0]
        val second = candidates[1]
        // Refuse a tie or near-tie rather than risk clicking a sibling control.
        return best.first.takeIf { second.second - best.second >= MIN_DISTANCE_MARGIN_PX }
    }

    private fun labels(node: StructuralNodeMetadata): Set<String> =
        listOfNotNull(node.text, node.contentDescription)
            .mapNotNull { value ->
                value.trim().lowercase().replace(Regex("\\s+"), " ").takeIf(String::isNotEmpty)
            }
            .toSet()

    private fun centerDistance(first: ScreenBounds, second: ScreenBounds): Double {
        val firstX = (first.left + first.right) / 2.0
        val firstY = (first.top + first.bottom) / 2.0
        val secondX = (second.left + second.right) / 2.0
        val secondY = (second.top + second.bottom) / 2.0
        return kotlin.math.hypot(firstX - secondX, firstY - secondY)
    }

    private companion object {
        const val MIN_DISTANCE_MARGIN_PX = 32.0
    }
}
