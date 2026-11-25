package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundWindowValidationPolicyTest {
    private fun evaluate(
        snapshotPackage: String? = TARGET,
        rootPackage: String? = TARGET,
        rootWindowId: Int? = 7,
        activeWindowId: Int? = 7,
        activeWindowPackage: String? = TARGET,
        activeWindowType: Int? = APPLICATION,
        activeWindowIsActive: Boolean = true,
        serviceConnected: Boolean = true,
        windowsSnapshotStatus: ForegroundWindowValidationPolicy.WindowsSnapshotStatus = ForegroundWindowValidationPolicy.WindowsSnapshotStatus.AVAILABLE
    ) = ForegroundWindowValidationPolicy.evaluate(
        expectedPackage = TARGET,
        snapshotPackage = snapshotPackage,
        rootPackage = rootPackage,
        rootWindowId = rootWindowId,
        activeWindowId = activeWindowId,
        activeWindowPackage = activeWindowPackage,
        activeWindowType = activeWindowType,
        applicationWindowType = APPLICATION,
        activeWindowIsActive = activeWindowIsActive,
        serviceConnected = serviceConnected,
        windowsSnapshotStatus = windowsSnapshotStatus
    )

    @Test fun acceptsOnlyTheCurrentActiveTargetApplicationWindow() {
        assertTrue(evaluate().isValid)
    }

    @Test fun rejectsDisconnectedServiceAndMissingRoot() {
        assertEquals(ForegroundWindowValidationPolicy.Status.SERVICE_DISCONNECTED, evaluate(serviceConnected = false).status)
        assertEquals(ForegroundWindowValidationPolicy.Status.ROOT_MISSING, evaluate(rootWindowId = null).status)
    }

    @Test fun rejectsSnapshotOrRootFromAnotherPackage() {
        assertEquals(ForegroundWindowValidationPolicy.Status.SNAPSHOT_PACKAGE_MISMATCH, evaluate(snapshotPackage = "system.ui").status)
        assertEquals(ForegroundWindowValidationPolicy.Status.ROOT_PACKAGE_MISMATCH, evaluate(rootPackage = "system.ui").status)
    }

    @Test fun rejectsWindowIdentityAndTypeMismatch() {
        assertEquals(ForegroundWindowValidationPolicy.Status.ACTIVE_WINDOW_MISSING, evaluate(activeWindowId = null).status)
        assertEquals(ForegroundWindowValidationPolicy.Status.WINDOW_ID_MISMATCH, evaluate(activeWindowId = 8).status)
        assertEquals(ForegroundWindowValidationPolicy.Status.WINDOW_PACKAGE_MISMATCH, evaluate(activeWindowPackage = "system.ui").status)
        assertEquals(ForegroundWindowValidationPolicy.Status.NON_APPLICATION_WINDOW, evaluate(activeWindowType = 2).status)
    }

    @Test fun rejectsWindowThatIsNoLongerActive() {
        val result = evaluate(activeWindowIsActive = false)
        assertFalse(result.isValid)
        assertEquals(ForegroundWindowValidationPolicy.Status.WINDOW_NOT_ACTIVE, result.status)
    }


    @Test fun allowsCaptureButNeverClickWhenTargetRootIsValidAndWindowListIsConfirmedEmpty() {
        val result = evaluate(windowsSnapshotStatus = ForegroundWindowValidationPolicy.WindowsSnapshotStatus.EMPTY)
        assertEquals(ForegroundWindowValidationPolicy.Status.ROOT_VERIFIED_WINDOWS_UNAVAILABLE, result.status)
        assertTrue(result.isCaptureValid)
        assertFalse(result.isValid)
    }

    @Test fun rejectsCaptureWhenInteractiveWindowRetrievalFails() {
        val result = evaluate(windowsSnapshotStatus = ForegroundWindowValidationPolicy.WindowsSnapshotStatus.FAILED)
        assertEquals(ForegroundWindowValidationPolicy.Status.WINDOW_SNAPSHOT_FAILED, result.status)
        assertFalse(result.isCaptureValid)
        assertFalse(result.isValid)
    }

    @Test fun rejectsAnotherForegroundPackageEvenWhenWindowListIsUnavailable() {
        val result = evaluate(rootPackage = "com.android.systemui", windowsSnapshotStatus = ForegroundWindowValidationPolicy.WindowsSnapshotStatus.EMPTY)
        assertEquals(ForegroundWindowValidationPolicy.Status.ROOT_PACKAGE_MISMATCH, result.status)
        assertFalse(result.isCaptureValid)
        assertFalse(result.isValid)
    }

    @Test fun rejectsMissingOrInvalidRootWindowIdsWhenWindowListIsUnavailable() {
        val missing = evaluate(rootWindowId = null, windowsSnapshotStatus = ForegroundWindowValidationPolicy.WindowsSnapshotStatus.EMPTY)
        val invalid = evaluate(rootWindowId = -1, windowsSnapshotStatus = ForegroundWindowValidationPolicy.WindowsSnapshotStatus.EMPTY)
        assertEquals(ForegroundWindowValidationPolicy.Status.ROOT_MISSING, missing.status)
        assertEquals(ForegroundWindowValidationPolicy.Status.ROOT_MISSING, invalid.status)
        assertFalse(missing.isCaptureValid)
        assertFalse(invalid.isCaptureValid)
    }

    private companion object {
        const val TARGET = "com.funnyvideo.cgame.fruitblast"
        const val APPLICATION = 1
    }
}
