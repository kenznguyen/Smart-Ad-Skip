package com.kenznguyen.smartadskip.accessibility

internal object ForegroundWindowValidationPolicy {
    enum class Status {
        VALID, ROOT_VERIFIED_WINDOWS_UNAVAILABLE, SERVICE_DISCONNECTED, SNAPSHOT_PACKAGE_MISMATCH, ROOT_MISSING,
        ROOT_PACKAGE_MISMATCH, ACTIVE_WINDOW_MISSING, WINDOW_ID_MISMATCH,
        WINDOW_PACKAGE_MISMATCH, NON_APPLICATION_WINDOW, WINDOW_NOT_ACTIVE
    }

    data class Result(val status: Status) {
        val isValid: Boolean get() = status == Status.VALID
        val isCaptureValid: Boolean get() = status == Status.VALID || status == Status.ROOT_VERIFIED_WINDOWS_UNAVAILABLE
    }

    fun evaluate(
        expectedPackage: String,
        snapshotPackage: String?,
        rootPackage: String?,
        rootWindowId: Int?,
        activeWindowId: Int?,
        activeWindowPackage: String?,
        activeWindowType: Int?,
        applicationWindowType: Int,
        activeWindowIsActive: Boolean,
        serviceConnected: Boolean,
        windowsSnapshotAvailable: Boolean = true
    ): Result {
        if (!serviceConnected) return Result(Status.SERVICE_DISCONNECTED)
        if (snapshotPackage != expectedPackage) return Result(Status.SNAPSHOT_PACKAGE_MISMATCH)
        if (rootWindowId == null || rootPackage == null) return Result(Status.ROOT_MISSING)
        if (rootPackage != expectedPackage) return Result(Status.ROOT_PACKAGE_MISMATCH)
        // rootInActiveWindow is the active-window root API. When OEM firmware returns
        // an empty/unavailable getWindows() snapshot, allow capture only from this
        // verified target root; isValid remains false so click authorization stays strict.
        if (!windowsSnapshotAvailable) return Result(Status.ROOT_VERIFIED_WINDOWS_UNAVAILABLE)
        if (activeWindowId == null || activeWindowType == null) return Result(Status.ACTIVE_WINDOW_MISSING)
        if (activeWindowId != rootWindowId) return Result(Status.WINDOW_ID_MISMATCH)
        if (activeWindowPackage != expectedPackage) return Result(Status.WINDOW_PACKAGE_MISMATCH)
        if (activeWindowType != applicationWindowType) return Result(Status.NON_APPLICATION_WINDOW)
        if (!activeWindowIsActive) return Result(Status.WINDOW_NOT_ACTIVE)
        return Result(Status.VALID)
    }
}
