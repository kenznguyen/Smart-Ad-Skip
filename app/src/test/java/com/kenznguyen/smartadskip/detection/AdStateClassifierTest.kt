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
    fun classifiesAdContainerWithoutSkipControlAsPlaying() {
        val result = classifier.classify(snapshot(nodes = listOf(adNode())), now)

        assertEquals(AdState.AD_PLAYING, result)
    }

    @Test
    fun classifiesClickableSkipControlOnlyWhenAdContainerIsPresent() {
        val result = classifier.classify(
            snapshot(nodes = listOf(adNode(), skipNode())),
            now
        )

        assertEquals(AdState.SKIP_AVAILABLE, result)
    }

    @Test
    fun clickableNodeWithoutSemanticResourceIdRemainsUnknown() {
        val result = classifier.classify(
            snapshot(nodes = listOf(adNode(), node(resourceId = null, clickable = true))),
            now
        )

        assertEquals(AdState.AD_PLAYING, result)
    }

    @Test
    fun skipNamedNodeWithoutAdContextRemainsUnknown() {
        val result = classifier.classify(snapshot(nodes = listOf(skipNode())), now)

        assertEquals(AdState.UNKNOWN, result)
    }

    @Test
    fun classifiesAdCompletedOnlyAfterObservedAdContainerDisappearsFromNonEmptyTree() {
        val previous = snapshot(nodes = listOf(adNode()), timestamp = now - 1_000L)
        val current = snapshot(nodes = listOf(node("com.game:id/game_root")), timestamp = now)

        val result = classifier.classify(current, now, previous)

        assertEquals(AdState.AD_COMPLETED, result)
    }

    @Test
    fun missingTreeDoesNotProveAdCompleted() {
        val previous = snapshot(nodes = listOf(adNode()), timestamp = now - 1_000L)
        val current = snapshot(nodes = emptyList(), timestamp = now)

        val result = classifier.classify(current, now, previous)

        assertEquals(AdState.UNKNOWN, result)
    }

    @Test
    fun staleSnapshotAlwaysRemainsUnknown() {
        val result = classifier.classify(
            snapshot(nodes = listOf(adNode(), skipNode()), timestamp = now - 16_000L),
            now
        )

        assertEquals(AdState.UNKNOWN, result)
    }

    @Test
    fun differentPackageDoesNotProveAdCompleted() {
        val previous = snapshot(nodes = listOf(adNode()), timestamp = now - 1_000L)
        val current = snapshot(
            packageName = "com.other.app",
            nodes = listOf(node("com.other:id/root")),
            timestamp = now
        )

        assertEquals(AdState.UNKNOWN, classifier.classify(current, now, previous))
    }


    @Test
    fun mbridgeRewardVideoActivityWithFreshNonEmptyTreeIsAdPlaying() {
        val current = snapshot(
            packageName = "com.funnyvideo.cgame.fruitblast",
            nodes = listOf(node("miui.systemui.plugin.id/header_carrier_view")),
        ).copy(windowClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")

        assertEquals(AdState.AD_PLAYING, classifier.classify(current, now))
    }

    @Test
    fun mbridgeRewardVideoActivityWithStaleSnapshotRemainsUnknown() {
        val current = snapshot(
            packageName = "com.funnyvideo.cgame.fruitblast",
            nodes = listOf(node("miui.systemui.plugin.id/header_carrier_view")),
            timestamp = now - 16_000L
        ).copy(windowClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")

        assertEquals(AdState.UNKNOWN, classifier.classify(current, now))
    }

    @Test
    fun mbridgeActivityWithoutStructuralNodesRemainsUnknown() {
        val current = snapshot(
            packageName = "com.funnyvideo.cgame.fruitblast",
            nodes = emptyList()
        ).copy(windowClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")

        assertEquals(AdState.UNKNOWN, classifier.classify(current, now))
    }

    @Test
    fun rejectsSystemUiAndScreenRecorderPackagesAsIrrelevantSources() {
        assertEquals(false, classifier.isRelevantPackage("com.android.systemui"))
        assertEquals(false, classifier.isRelevantPackage("com.miui.screenrecorder"))
        assertEquals(true, classifier.isRelevantPackage("com.funnyvideo.cgame.fruitblast"))
    }

    @Test
    fun recognizesKnownRewardedAdSdkActivitySignatures() {
        val signatures = listOf(
            "com.mbridge.msdk.reward.player.MBRewardVideoActivity",
            "com.google.android.gms.ads.AdActivity",
            "com.unity3d.services.ads.adunit.AdUnitActivity",
            "com.applovin.adview.AppLovinFullscreenActivity",
            "com.facebook.ads.AudienceNetworkActivity"
        )

        signatures.forEach { signature ->
            val current = snapshot(
                packageName = "com.funnyvideo.cgame.fruitblast",
                nodes = listOf(node(null))
            ).copy(windowClassName = signature)
            assertEquals(signature, AdState.AD_PLAYING, classifier.classify(current, now))
        }
    }

    @Test
    fun sdkActivityTransitionToHostAppWindowProvesAdCompleted() {
        val previous = snapshot(
            packageName = "com.funnyvideo.cgame.fruitblast",
            nodes = listOf(node(null)),
            timestamp = now - 1_000L
        ).copy(windowClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")
        val current = snapshot(
            packageName = "com.funnyvideo.cgame.fruitblast",
            nodes = listOf(node(null)),
            timestamp = now
        ).copy(windowClassName = "com.funnyvideo.cgame.fruitblast.GameView")

        assertEquals(AdState.AD_COMPLETED, classifier.classify(current, now, previous))
    }

    @Test
    fun foreignPackageEventCannotCompleteOrReplaceAdState() {
        val previous = snapshot(
            packageName = "com.funnyvideo.cgame.fruitblast",
            nodes = listOf(node(null)),
            timestamp = now - 1_000L
        ).copy(windowClassName = "com.mbridge.msdk.reward.player.MBRewardVideoActivity")
        val current = snapshot(
            packageName = "com.android.systemui",
            nodes = listOf(node(null)),
            timestamp = now
        )

        assertEquals(AdState.UNKNOWN, classifier.classify(current, now, previous))
    }

    @Test
    fun genericWebViewOrButtonDoesNotCountAsAdEvidence() {
        val current = snapshot(
            packageName = "com.funnyvideo.cgame.fruitblast",
            nodes = listOf(
                node(null, clickable = true).copy(viewClassName = "android.webkit.WebView"),
                node(null, clickable = true).copy(viewClassName = "android.widget.Button")
            )
        )

        assertEquals(AdState.UNKNOWN, classifier.classify(current, now))
    }

    @Test
    fun unrelatedWindowClassDoesNotTriggerMbridgeDetection() {
        val current = snapshot(
            packageName = "com.funnyvideo.cgame.fruitblast",
            nodes = listOf(node("miui.systemui.plugin.id/header_carrier_view"))
        ).copy(windowClassName = "com.example.GameActivity")

        assertEquals(AdState.UNKNOWN, classifier.classify(current, now))
    }

    private fun snapshot(
        packageName: String = "com.funnyvideo.cgame.fruitblast",
        nodes: List<StructuralNodeMetadata>,
        timestamp: Long = now
    ) = AccessibilitySnapshot(
        packageName = packageName,
        eventType = 32,
        windowClassName = "android.widget.FrameLayout",
        nodeCount = nodes.size,
        clickableNodeCount = nodes.count { it.clickable },
        observedAtEpochMillis = timestamp,
        structuralNodes = nodes
    )

    private fun adNode() = node("com.funnyvideo.cgame.fruitblast:id/interstitial_ad_container")
    private fun skipNode() = node("com.funnyvideo.cgame.fruitblast:id/skip_button", clickable = true)

    private fun node(
        resourceId: String?,
        clickable: Boolean = false
    ) = StructuralNodeMetadata(
        viewClassName = "android.widget.FrameLayout",
        resourceId = resourceId,
        clickable = clickable,
        bounds = ScreenBounds(0, 0, 100, 50)
    )
}
