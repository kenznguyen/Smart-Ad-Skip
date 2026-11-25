package com.kenznguyen.smartadskip.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class Qwen3VlOutputParserTest {
    @Test fun parsesIndependentCloseDecision() {
        val result = Qwen3VlOutputParser.parse(
            """{"action":"CLOSE","targetElementId":"node-4","confidence":0.96,"rationale":"Visible X control"}"""
        )
        assertEquals(AdAction.CLOSE, result.action)
        assertEquals("node-4", result.targetElementId)
        assertEquals(0.96f, result.confidence)
    }

    @Test fun parsesContinueDecisionForPausedAd() {
        val result = Qwen3VlOutputParser.parse(
            """{"action":"CONTINUE","targetElementId":"node-2","confidence":0.93,"rationale":"Ad playback is paused"}"""
        )
        assertEquals(AdAction.CONTINUE, result.action)
        assertEquals("node-2", result.targetElementId)
    }

    @Test fun parsesWaitWithoutTarget() {
        val result = Qwen3VlOutputParser.parse(
            """{"action":"WAIT","targetElementId":null,"confidence":0.99,"rationale":"No clear control"}"""
        )
        assertEquals(AdAction.WAIT, result.action)
        assertNull(result.targetElementId)
    }

    @Test fun rejectsUnknownActionAndMalformedJson() {
        assertThrows(IllegalArgumentException::class.java) {
            Qwen3VlOutputParser.parse("""{"action":"CLICK","confidence":0.99}""")
        }
        assertThrows(Exception::class.java) {
            Qwen3VlOutputParser.parse("not-json")
        }
    }
}
