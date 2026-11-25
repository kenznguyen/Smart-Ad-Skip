package com.kenznguyen.smartadskip.accessibility

/** Diagnostic classification only; it never grants click authorization. */
internal object ForegroundWindowDiagnostics {
    enum class WindowRole { APPLICATION, OVERLAY, SYSTEM, UNKNOWN }

    fun classifyWindowType(type: Int?, applicationType: Int, systemType: Int): WindowRole =
        when (type) {
            null -> WindowRole.UNKNOWN
            applicationType -> WindowRole.APPLICATION
            systemType -> WindowRole.SYSTEM
            else -> WindowRole.OVERLAY
        }

    fun transitionKey(
        eventPackage: String?,
        eventClass: String?,
        rootPackage: String?,
        rootWindowId: Int?,
        activeWindowId: Int?,
        activeWindowType: Int?,
        activeWindowPackage: String?
    ): String = listOf(
        eventPackage ?: "null", eventClass ?: "null", rootPackage ?: "null",
        rootWindowId?.toString() ?: "null", activeWindowId?.toString() ?: "null",
        activeWindowType?.toString() ?: "null", activeWindowPackage ?: "null"
    ).joinToString("|")
}
