package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrActionPolicyTest {
    private val policy = OcrActionPolicy()

    @Test
    fun recognizesOnlyCloseDismissAndStandaloneX() {
        assertEquals(VisualActionType.CLOSE, policy.resolveCandidate("Close"))
        assertEquals(VisualActionType.CLOSE, policy.resolveCandidate("Dismiss"))
        assertEquals(VisualActionType.CLOSE, policy.resolveCandidate("X"))
    }

    @Test
    fun nextAndSkipLabelsNeverResolveToActions() {
        listOf("Next", "Skip", "Skip Ad", "Next Ad", ">>").forEach {
            assertNull("$it must not resolve", policy.resolveCandidate(it))
        }
    }

    @Test
    fun protectedLabelsAlwaysRejectCandidate() {
        listOf(
            "Install", "Download", "Buy now", "Purchase", "Claim reward",
            "Collect", "Reward", "Shop", "Sponsored", "Continue", "Open",
            "Visit", "Subscribe", "Play Now", "Learn More", "Get Started",
            "Get It", "Sign Up", "Watch Now"
        ).forEach { assertNull("$it must be protected", policy.resolveCandidate(it)) }
    }

    @Test
    fun ambiguousRecognizedTextDoesNotBecomeAnAction() {
        assertNull(policy.resolveCandidate("limited time offer"))
    }

    @Test
    fun noTextRequiresPositiveCloseSymbolDiscovery() {
        assertNull(policy.resolveCandidate(""))
        assertEquals(
            VisualActionType.CLOSE,
            policy.resolveCandidate("", symbolDiscovery = VisualActionType.CLOSE)
        )
        assertNull(policy.resolveCandidate("", symbolDiscovery = VisualActionType.NEXT_AD))
        assertNull(policy.resolveCandidate("", symbolDiscovery = VisualActionType.UNKNOWN))
    }

    @Test
    fun recognizedTextCannotBeOverriddenBySymbolDiscovery() {
        assertEquals(
            VisualActionType.CLOSE,
            policy.resolveCandidate("Close", symbolDiscovery = VisualActionType.NEXT_AD)
        )
        assertNull(policy.resolveCandidate("Install", symbolDiscovery = VisualActionType.CLOSE))
    }
}
