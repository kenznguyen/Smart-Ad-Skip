package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualActionDetectorTest {
    private val detector = VisualActionDetector(minimumConfidence = 0.70f)

    @Test
    fun detectsWhiteXOnGrayCircleAsClose() {
        val match = detector.detect(closeFrame())
        assertEquals(VisualActionType.CLOSE, match.type)
        assertTrue(match.confidence >= 0.70f)
    }

    @Test
    fun rejectsFormerNextAdChevronAndPlainAdSurface() {
        val nextAdLikeFrame = nextAdFrame()
        assertEquals(VisualActionType.UNKNOWN, detector.detect(nextAdLikeFrame).type)

        val blank = VisualFrame(64, 64, IntArray(64 * 64) { rgb(190, 120, 90) })
        assertEquals(VisualActionType.UNKNOWN, detector.detect(blank).type)
    }

    @Test
    fun rejectsTinyFrames() {
        assertEquals(
            VisualActionType.UNKNOWN,
            detector.detect(VisualFrame(2, 2, IntArray(4) { rgb(255, 255, 0) })).type
        )
    }

    private fun nextAdFrame(): VisualFrame {
        val size = 64
        val pixels = IntArray(size * size) { rgb(170, 105, 85) }
        circle(pixels, size, 32, 32, 27, rgb(255, 220, 0), ring = true)
        line(pixels, size, 24, 22, 33, 32, rgb(255, 255, 255), 3)
        line(pixels, size, 33, 32, 24, 42, rgb(255, 255, 255), 3)
        line(pixels, size, 34, 22, 43, 32, rgb(255, 255, 255), 3)
        line(pixels, size, 43, 32, 34, 42, rgb(255, 255, 255), 3)
        return VisualFrame(size, size, pixels)
    }

    private fun closeFrame(): VisualFrame {
        val size = 64
        val pixels = IntArray(size * size) { rgb(245, 245, 245) }
        circle(pixels, size, 32, 32, 27, rgb(140, 140, 140), ring = false)
        line(pixels, size, 25, 25, 39, 39, rgb(255, 255, 255), 3)
        line(pixels, size, 39, 25, 25, 39, rgb(255, 255, 255), 3)
        return VisualFrame(size, size, pixels)
    }

    private fun circle(p: IntArray, size: Int, cx: Int, cy: Int, radius: Int, color: Int, ring: Boolean) {
        for (y in 0 until size) for (x in 0 until size) {
            val d = (x - cx) * (x - cx) + (y - cy) * (y - cy)
            if (d <= radius * radius && (!ring || d >= (radius - 5) * (radius - 5))) p[y * size + x] = color
        }
    }

    private fun line(p: IntArray, size: Int, x1: Int, y1: Int, x2: Int, y2: Int, color: Int, thickness: Int) {
        val steps = maxOf(kotlin.math.abs(x2 - x1), kotlin.math.abs(y2 - y1))
        for (step in 0..steps) {
            val x = x1 + (x2 - x1) * step / steps
            val y = y1 + (y2 - y1) * step / steps
            for (dy in -thickness..thickness) for (dx in -thickness..thickness) {
                if (x + dx in 0 until size && y + dy in 0 until size) p[(y + dy) * size + x + dx] = color
            }
        }
    }

    private fun rgb(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b
}
