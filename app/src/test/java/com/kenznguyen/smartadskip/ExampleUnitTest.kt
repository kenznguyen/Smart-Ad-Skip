package com.kenznguyen.smartadskip

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun initialVersionName_isZeroPointOneZero() {
        assertEquals("0.1.0", BuildConfig.VERSION_NAME)
    }
}
