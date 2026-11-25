package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundWindowDiagnosticsTest {
    @Test fun distinguishesApplicationOverlaySystemAndUnknownWindows() {
        assertEquals(ForegroundWindowDiagnostics.WindowRole.APPLICATION,
            ForegroundWindowDiagnostics.classifyWindowType(1, 1, 3))
        assertEquals(ForegroundWindowDiagnostics.WindowRole.SYSTEM,
            ForegroundWindowDiagnostics.classifyWindowType(3, 1, 3))
        assertEquals(ForegroundWindowDiagnostics.WindowRole.OVERLAY,
            ForegroundWindowDiagnostics.classifyWindowType(2, 1, 3))
        assertEquals(ForegroundWindowDiagnostics.WindowRole.UNKNOWN,
            ForegroundWindowDiagnostics.classifyWindowType(null, 1, 3))
    }

    @Test fun transitionKeyChangesWhenPackageOrWindowIdentityChanges() {
        val first = ForegroundWindowDiagnostics.transitionKey("fruit", "GameActivity", "fruit", 7, 7, 1, "fruit")
        val next = ForegroundWindowDiagnostics.transitionKey("fruit", "AdActivity", "fruit", 8, 8, 1, "fruit")
        val overlay = ForegroundWindowDiagnostics.transitionKey("system", "Dialog", "system", 9, 9, 2, "system")
        assertEquals(false, first == next)
        assertEquals(false, next == overlay)
        assertEquals(first, ForegroundWindowDiagnostics.transitionKey("fruit", "GameActivity", "fruit", 7, 7, 1, "fruit"))
    }
}
