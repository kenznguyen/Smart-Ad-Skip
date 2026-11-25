package com.kenznguyen.smartadskip.detection

import com.kenznguyen.smartadskip.accessibility.AccessibilitySnapshot
import com.kenznguyen.smartadskip.accessibility.ScreenBounds
import com.kenznguyen.smartadskip.accessibility.StructuralNodeMetadata
import org.junit.Assert.assertEquals
import org.junit.Test

class AdStateClassifierTest {
    private val classifier = AdStateClassifier()
    private val now = 1_800_000_000_000L

    @Test
    fun visualActionAloneCannotEstablishAdContext() {
        val current = snapshot(nodes = listOf(node(null, clickable = true)))
        assertEquals(AdState.UNKNOWN, classifier.classify(current, now, hasDetectedAction = true))
    }

    @Test
    fun verifiedVisualActionIsAllowedOnlyInsideKnownAdSdkActivity() {
        val current = snapshot(nodes = listOf(node(null, clickable = true)))
            .copy(activityClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")
        assertEquals(AdState.SKIP_AVAILABLE, classifier.classify(current, now, hasDetectedAction = true))
    }

    @Test
    fun continueDecisionEstablishesPausedStateOnlyInsideKnownAdActivity() {
        val current = snapshot(nodes = listOf(node(null, clickable = true)))
            .copy(activityClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")
        assertEquals(
            AdState.AD_PAUSED,
            classifier.classify(current, now, hasDetectedAction = true, hasDetectedContinueAction = true)
        )
    }

    @Test
    fun continueDecisionCannotEstablishPausedStateWithoutKnownAdActivity() {
        val current = snapshot(nodes = listOf(node(null, clickable = true)))
        assertEquals(
            AdState.UNKNOWN,
            classifier.classify(current, now, hasDetectedAction = true, hasDetectedContinueAction = true)
        )
    }

    @Test
    fun sourceViewClassCannotEstablishAdContextWithoutActivityIdentity() {
        val current = snapshot(nodes = listOf(node(null)))
            .copy(windowClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")
        assertEquals(AdState.UNKNOWN, classifier.classify(current, now))
    }

    @Test
    fun activityIdentityEstablishesAdContextEvenWhenSourceViewIsGeneric() {
        val current = snapshot(nodes = listOf(node(null)))
            .copy(
                windowClassName = "android.view.View",
                activityClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity"
            )
        assertEquals(AdState.AD_PLAYING, classifier.classify(current, now))
    }

    @Test
    fun resourceIdsDoNotEstablishAdContext() {
        val current = snapshot(nodes = listOf(node("com.game:id/interstitial_ad_container")))
        assertEquals(AdState.UNKNOWN, classifier.classify(current, now))
    }

    @Test
    fun genericClickableNodeDoesNotEstablishAdContext() {
        val current = snapshot(nodes = listOf(node(null, clickable = true)))
        assertEquals(AdState.UNKNOWN, classifier.classify(current, now))
    }

    @Test
    fun knownSdkActivityEstablishesAdPlaying() {
        val current = snapshot(nodes = listOf(node(null)))
            .copy(activityClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")
        assertEquals(AdState.AD_PLAYING, classifier.classify(current, now))
    }

    @Test
    fun sdkActivityTransitionToHostWindowProvesAdCompleted() {
        val previous = snapshot(nodes = listOf(node(null)), timestamp = now - 1_000L)
            .copy(activityClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")
        val current = snapshot(nodes = listOf(node(null)))
        assertEquals(AdState.AD_COMPLETED, classifier.classify(current, now, previous))
    }

    @Test
    fun staleSnapshotRemainsUnknownEvenWithVisualAction() {
        val current = snapshot(nodes = listOf(node(null)), timestamp = now - 16_000L)
        assertEquals(AdState.UNKNOWN, classifier.classify(current, now, hasDetectedAction = true))
    }

    @Test
    fun wrongPackageRemainsUnknown() {
        val current = snapshot(packageName = "com.other.app", nodes = listOf(node(null)))
        assertEquals(AdState.UNKNOWN, classifier.classify(current, now, hasDetectedAction = true))
    }

    @Test
    fun emptyTreeDoesNotEstablishAdContext() {
        assertEquals(AdState.UNKNOWN, classifier.classify(snapshot(nodes = emptyList()), now, hasDetectedAction = true))
    }

    @Test
    fun ignoresSystemUiAndRecorderPackages() {
        assertEquals(false, classifier.isRelevantPackage("com.android.systemui"))
        assertEquals(false, classifier.isRelevantPackage("com.miui.screenrecorder"))
        assertEquals(true, classifier.isRelevantPackage("com.funnyvideo.cgame.fruitblast"))
    }

    private fun snapshot(
        packageName: String = "com.funnyvideo.cgame.fruitblast",
        nodes: List<StructuralNodeMetadata>,
        timestamp: Long = now
    ) = AccessibilitySnapshot(
        packageName = packageName,
        eventType = 32,
        windowClassName = "android.view.View",
        nodeCount = nodes.size,
        clickableNodeCount = nodes.count { it.clickable },
        observedAtEpochMillis = timestamp,
        structuralNodes = nodes
    )

    private fun node(resourceId: String?, clickable: Boolean = false) =
        StructuralNodeMetadata(
            viewClassName = "android.view.View",
            resourceId = resourceId,
            clickable = clickable,
            bounds = ScreenBounds(0, 0, 100, 50)
        )
}
