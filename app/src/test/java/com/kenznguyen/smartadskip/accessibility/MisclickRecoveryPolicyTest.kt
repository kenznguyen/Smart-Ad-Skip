package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MisclickRecoveryPolicyTest {
    private val policy = MisclickRecoveryPolicy(maxBackAttempts = 2)

    @Test
    fun keepsNormalTargetWindowWithoutSendingBack() {
        policy.onClickDispatched()

        assertEquals(
            MisclickRecoveryPolicy.Decision.TARGET_CONFIRMED,
            policy.onWindowObserved(TARGET_PACKAGE, targetWindowValid = true)
        )
        assertFalse(policy.isPending)
    }

    @Test
    fun requestsAtMostTwoBackActionsAndWaitsForEachAttemptToSettle() {
        policy.onClickDispatched()

        assertEquals(
            MisclickRecoveryPolicy.Decision.REQUEST_BACK,
            policy.onWindowObserved("com.android.vending", targetWindowValid = false)
        )
        assertEquals(
            MisclickRecoveryPolicy.Decision.WAIT,
            policy.onWindowObserved("com.android.vending", targetWindowValid = false)
        )

        policy.onBackAttemptSettled()
        assertEquals(
            MisclickRecoveryPolicy.Decision.REQUEST_BACK,
            policy.onWindowObserved("com.android.vending", targetWindowValid = false)
        )

        policy.onBackAttemptSettled()
        assertEquals(
            MisclickRecoveryPolicy.Decision.EXHAUSTED,
            policy.onWindowObserved("com.android.vending", targetWindowValid = false)
        )
        assertFalse(policy.isPending)
    }

    @Test
    fun confirmsRecoveryOnlyAfterTargetWindowIsValid() {
        policy.onClickDispatched()
        assertEquals(
            MisclickRecoveryPolicy.Decision.REQUEST_BACK,
            policy.onWindowObserved("com.android.vending", targetWindowValid = false)
        )

        assertEquals(
            MisclickRecoveryPolicy.Decision.TARGET_CONFIRMED,
            policy.onWindowObserved(TARGET_PACKAGE, targetWindowValid = true)
        )
        assertFalse(policy.isPending)
    }

    @Test
    fun abortsWithoutBackWhenForegroundCannotBeVerified() {
        policy.onClickDispatched()

        assertEquals(
            MisclickRecoveryPolicy.Decision.ABORT_UNVERIFIED,
            policy.onWindowObserved(null, targetWindowValid = false)
        )
        assertFalse(policy.isPending)
    }

    @Test
    fun abortsWhenTargetPackageReturnsButWindowIsNotValidated() {
        policy.onClickDispatched()

        assertEquals(
            MisclickRecoveryPolicy.Decision.ABORT_UNVERIFIED,
            policy.onWindowObserved(TARGET_PACKAGE, targetWindowValid = false)
        )
        assertFalse(policy.isPending)
    }

    private companion object {
        const val TARGET_PACKAGE = "com.funnyvideo.cgame.fruitblast"
    }
}
