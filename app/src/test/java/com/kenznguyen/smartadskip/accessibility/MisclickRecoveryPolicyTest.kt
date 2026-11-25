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
        assertEquals(MisclickRecoveryPolicy.Decision.REQUEST_BACK, policy.onWindowObserved("com.android.vending", false))
        assertEquals(MisclickRecoveryPolicy.Decision.WAIT, policy.onWindowObserved("com.android.vending", false))
        policy.onBackAttemptSettled()
        assertEquals(MisclickRecoveryPolicy.Decision.REQUEST_BACK, policy.onWindowObserved("com.android.vending", false))
        policy.onBackAttemptSettled()
        assertEquals(MisclickRecoveryPolicy.Decision.EXHAUSTED, policy.onWindowObserved("com.android.vending", false))
        assertFalse(policy.isPending)
    }

    @Test
    fun confirmsRecoveryOnlyAfterTargetWindowIsValid() {
        policy.onClickDispatched()
        assertEquals(MisclickRecoveryPolicy.Decision.REQUEST_BACK, policy.onWindowObserved("com.android.vending", false))
        assertEquals(
            MisclickRecoveryPolicy.Decision.TARGET_CONFIRMED,
            policy.onWindowObserved(TARGET_PACKAGE, true)
        )
        assertFalse(policy.isPending)
    }

    @Test
    fun abortsWithoutBackWhenForegroundCannotBeVerified() {
        policy.onClickDispatched()
        assertEquals(
            MisclickRecoveryPolicy.Decision.ABORT_UNVERIFIED,
            policy.onWindowObserved(null, false)
        )
        assertFalse(policy.isPending)
    }

    @Test
    fun abortsWhenTargetPackageReturnsButWindowIsNotValidated() {
        policy.onClickDispatched()
        assertEquals(
            MisclickRecoveryPolicy.Decision.ABORT_UNVERIFIED,
            policy.onWindowObserved(TARGET_PACKAGE, false)
        )
        assertFalse(policy.isPending)
    }

    @Test
    fun refusesBackForUnapprovedExternalApplications() {
        policy.onClickDispatched()
        assertEquals(
            MisclickRecoveryPolicy.Decision.ABORT_UNVERIFIED,
            policy.onWindowObserved("com.openai.chatgpt", false)
        )
        assertFalse(policy.isPending)
        assertEquals(0, policy.attempts)
    }

    @Test
    fun permitsPlayStoreAndKnownBrowsersForBoundedBackRecovery() {
        listOf("com.android.vending", "com.android.chrome", "com.android.browser").forEach { packageName ->
            policy.reset()
            policy.onClickDispatched()
            assertEquals(
                MisclickRecoveryPolicy.Decision.REQUEST_BACK,
                policy.onWindowObserved(packageName, false)
            )
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.funnyvideo.cgame.fruitblast"
    }
}
