package com.kenznguyen.smartadskip.accessibility

import com.kenznguyen.smartadskip.detection.AdState
import com.kenznguyen.smartadskip.detection.AdStateClassifier
import com.kenznguyen.smartadskip.accessibility.AccessibilitySnapshot
import com.kenznguyen.smartadskip.accessibility.ScreenBounds
import com.kenznguyen.smartadskip.accessibility.StructuralNodeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRecoveryRegressionTest {
    private val classifier = AdStateClassifier()
    private val executionPolicy = ActionExecutionPolicy()
    private val recoveryPolicy = MisclickRecoveryPolicy(maxBackAttempts = 2)
    private val now = 1_800_000_000_000L

    @Test
    fun sdkAdToBrowserBackToSameAdRestoresOnlyValidatedAdActions() {
        val adSnapshot = snapshot(TIKTOK_REWARD_ACTIVITY)
        val initialState = classifier.classify(adSnapshot, now)
        assertEquals(AdState.AD_PLAYING, initialState)
        assertTrue(executionPolicy.canClick(initialState, VisualActionType.NEXT_AD))

        recoveryPolicy.onClickDispatched()
        assertEquals(
            MisclickRecoveryPolicy.Decision.REQUEST_BACK,
            recoveryPolicy.onWindowObserved("com.android.chrome", targetWindowValid = false)
        )
        assertEquals(
            MisclickRecoveryPolicy.Decision.TARGET_CONFIRMED,
            recoveryPolicy.onWindowObserved(TARGET_PACKAGE, targetWindowValid = true)
        )

        val returnedAd = snapshot(TIKTOK_REWARD_ACTIVITY, timestamp = now + 1_000L)
        val returnedState = classifier.classify(returnedAd, now + 1_000L)
        assertEquals(AdState.AD_PLAYING, returnedState)
        assertTrue(executionPolicy.canClick(returnedState, VisualActionType.CLOSE))
        assertFalse(executionPolicy.canClick(returnedState, VisualActionType.CONTINUE))
    }

    @Test
    fun returningToFruitBlastMainActivityEndsAdSessionWithoutClicking() {
        recoveryPolicy.onClickDispatched()
        assertEquals(
            MisclickRecoveryPolicy.Decision.REQUEST_BACK,
            recoveryPolicy.onWindowObserved("com.android.vending", targetWindowValid = false)
        )
        assertEquals(
            MisclickRecoveryPolicy.Decision.TARGET_CONFIRMED,
            recoveryPolicy.onWindowObserved(TARGET_PACKAGE, targetWindowValid = true)
        )

        val home = snapshot(MAIN_ACTIVITY, timestamp = now + 1_000L)
        val state = classifier.classify(home, now + 1_000L)
        assertEquals(AdState.GAME_HOME, state)
        assertFalse(executionPolicy.canClick(state, VisualActionType.NEXT_AD))
        assertFalse(executionPolicy.canClick(state, VisualActionType.CLOSE))
        assertFalse(executionPolicy.canClick(state, VisualActionType.CONTINUE))
    }

    @Test
    fun unknownExternalAppNeverReceivesAutomaticBackOrClick() {
        recoveryPolicy.onClickDispatched()
        assertEquals(
            MisclickRecoveryPolicy.Decision.ABORT_UNVERIFIED,
            recoveryPolicy.onWindowObserved("com.openai.chatgpt", targetWindowValid = false)
        )
        assertEquals(0, recoveryPolicy.attempts)
        assertFalse(executionPolicy.canClick(AdState.UNKNOWN, VisualActionType.NEXT_AD))
    }

    private fun snapshot(activity: String, timestamp: Long = now) = AccessibilitySnapshot(
        packageName = TARGET_PACKAGE,
        eventType = 32,
        windowClassName = "android.view.View",
        activityClassName = activity,
        nodeCount = 1,
        clickableNodeCount = 1,
        observedAtEpochMillis = timestamp,
        structuralNodes = listOf(
            StructuralNodeMetadata(
                viewClassName = "android.view.View",
                resourceId = null,
                clickable = true,
                bounds = ScreenBounds(0, 0, 100, 50)
            )
        )
    )

    private companion object {
        const val TARGET_PACKAGE = "com.funnyvideo.cgame.fruitblast"
        const val MAIN_ACTIVITY = "com.funnyvideo.cgame.fruitblast.MainActivity"
        const val TIKTOK_REWARD_ACTIVITY =
            "com.bytedance.sdk.openadsdk.activity.TTRewardExpressVideoActivity"
    }
}
