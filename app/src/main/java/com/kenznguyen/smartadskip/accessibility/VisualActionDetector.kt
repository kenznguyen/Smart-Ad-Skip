package com.kenznguyen.smartadskip.accessibility

/**
 * Pixel-only classifier for the two Fruit Blast ad controls approved for P2.6.
 * CONTINUE is intentionally AI-only: this detector does not infer paused-ad state.
 */
enum class VisualActionType { NEXT_AD, CLOSE, CONTINUE, UNKNOWN }

data class VisualFrame(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {
    init {
        require(width > 0 && height > 0)
        require(pixels.size == width * height)
    }

    fun pixel(x: Int, y: Int): Int = pixels[y * width + x]
}

data class VisualActionMatch(
    val type: VisualActionType,
    val confidence: Float
)

class VisualActionDetector(
    private val minimumConfidence: Float = 0.82f
) {
    fun detect(frame: VisualFrame): VisualActionMatch {
        val total = frame.pixels.size.toFloat()
        var yellow = 0
        var gray = 0
        var white = 0
        var dark = 0
        var xPositive = 0
        var xNegative = 0
        var chevronDown = 0
        var chevronUp = 0

        val cx = (frame.width - 1) / 2f
        val cy = (frame.height - 1) / 2f
        val radius = minOf(frame.width, frame.height) * 0.42f
        if (radius < 3f) return unknown()

        for (y in 0 until frame.height) {
            for (x in 0 until frame.width) {
                val color = frame.pixel(x, y)
                val r = color ushr 16 and 0xff
                val g = color ushr 8 and 0xff
                val b = color and 0xff
                val dx = x - cx
                val dy = y - cy
                val inButton = dx * dx + dy * dy <= radius * radius
                if (!inButton) continue

                if (r >= 175 && g >= 125 && b <= 105 && r - b >= 75) yellow++
                if (r in 95..185 && g in 95..185 && b in 95..185 &&
                    kotlin.math.abs(r - g) <= 18 && kotlin.math.abs(g - b) <= 18
                ) gray++
                if (r >= 220 && g >= 220 && b >= 220) {
                    white++
                    if (kotlin.math.abs(dx) >= 1f && kotlin.math.abs(dy) >= 1f) {
                        val slope = dy / dx
                        if (slope in 0.65f..1.55f) xPositive++
                        if (slope in -1.55f..-0.65f) xNegative++
                        if (dx > 0 && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 0.35f) chevronDown++
                        if (dx < 0 && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 0.35f) chevronUp++
                    }
                } else if (r <= 90 && g <= 90 && b <= 90) {
                    dark++
                }
            }
        }

        val yellowRatio = yellow / total
        val grayRatio = gray / total
        val whiteRatio = white / total
        val darkRatio = dark / total
        val nextGlyph = whiteRatio >= 0.025f && chevronDown + chevronUp >= 8
        val nextScore = minOf(1f, yellowRatio / 0.10f) *
            if (nextGlyph && darkRatio <= 0.35f) 1f else 0.45f
        if (yellowRatio >= 0.035f && nextGlyph && nextScore >= minimumConfidence) {
            return VisualActionMatch(VisualActionType.NEXT_AD, nextScore)
        }

        val closeGlyph = whiteRatio >= 0.018f && xPositive >= 3 && xNegative >= 3
        val closeScore = minOf(1f, grayRatio / 0.24f) *
            if (closeGlyph && yellowRatio < 0.02f) 1f else 0.40f
        if (grayRatio >= 0.10f && closeGlyph && closeScore >= minimumConfidence) {
            return VisualActionMatch(VisualActionType.CLOSE, closeScore)
        }
        return unknown()
    }

    private fun unknown() = VisualActionMatch(VisualActionType.UNKNOWN, 0f)
}
