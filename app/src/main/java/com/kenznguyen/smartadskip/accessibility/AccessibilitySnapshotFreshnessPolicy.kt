package com.kenznguyen.smartadskip.accessibility

internal object AccessibilitySnapshotFreshnessPolicy {
    const val MAX_AGE_MILLIS = 5_000L

    fun isFresh(observedAtEpochMillis: Long, nowEpochMillis: Long): Boolean =
        nowEpochMillis - observedAtEpochMillis in 0..MAX_AGE_MILLIS
}
