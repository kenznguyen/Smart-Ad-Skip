package com.kenznguyen.smartadskip.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Qwen3VlRuntimeConfigTest {
    @Test fun defaultsAreConservativeAndExplicit() {
        val config = Qwen3VlRuntimeConfig()
        assertEquals(2, config.cpuThreads)
        assertEquals(2048, config.contextSize)
        assertEquals(256, config.batchSize)
        assertEquals(960, config.maxImageEdge)
    }

    @Test fun rejectsUnsafeRuntimeParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            Qwen3VlRuntimeConfig(cpuThreads = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Qwen3VlRuntimeConfig(contextSize = 256)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Qwen3VlRuntimeConfig(batchSize = 2048)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Qwen3VlRuntimeConfig(maxImageEdge = 2000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Qwen3VlRuntimeConfig(inferenceTimeoutMillis = 100)
        }
    }
}
