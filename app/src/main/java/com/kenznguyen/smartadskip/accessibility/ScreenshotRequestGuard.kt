package com.kenznguyen.smartadskip.accessibility

/**
 * Contains synchronous failures from AccessibilityService.takeScreenshot().
 *
 * Screenshot rejection is fail-closed: callers are notified so in-flight state can
 * be released, and no screenshot processing or click path is entered.
 */
internal object ScreenshotRequestGuard {
    fun execute(
        request: () -> Unit,
        onFailure: () -> Unit
    ): Boolean {
        return try {
            request()
            true
        } catch (_: RuntimeException) {
            runCatching(onFailure)
            false
        }
    }
}
