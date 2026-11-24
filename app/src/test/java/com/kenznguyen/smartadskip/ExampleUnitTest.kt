package com.kenznguyen.smartadskip

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun initialVersionName_isZeroPointTwoEighteen() {
        assertEquals("0.2.18", BuildConfig.VERSION_NAME)
    }
}
