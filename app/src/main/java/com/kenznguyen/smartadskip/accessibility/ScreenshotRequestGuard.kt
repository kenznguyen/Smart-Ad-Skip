package com.kenznguyen.smartadskip.accessibility

/**
 * Contains synchronous failures from AccessibilityService.takeScreenshot().
 *
 * Screenshot rejection is fail-closed. The original RuntimeException is passed to
 * diagnostics, and no screenshot processing or click path is entered.
 */
internal object ScreenshotRequestGuard {
    fun execute(
        request: () -> Unit,
        onFailure: (RuntimeException) -> Unit
    ): Boolean {
        return try {
            request()
            true
        } catch (error: RuntimeException) {
            runCatching { onFailure(error) }
            false
        }
    }
}
