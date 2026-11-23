package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class OcrActionPolicyTest {
    private val policy = OcrActionPolicy()

    @Test
    fun recognizesNextAndSkipLabelsAsNextAdCandidatesWithoutVisualInput() {
        assertEquals(VisualActionType.NEXT_AD, policy.resolveCandidate("Next"))
        assertEquals(VisualActionType.NEXT_AD, policy.resolveCandidate("Skip Ad"))
    }

    @Test
    fun recognizesCloseAndDismissLabelsAsCloseCandidatesWithoutVisualInput() {
        assertEquals(VisualActionType.CLOSE, policy.resolveCandidate("Close"))
        assertEquals(VisualActionType.CLOSE, policy.resolveCandidate("Dismiss"))
        assertEquals(VisualActionType.CLOSE, policy.resolveCandidate("X"))
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
    fun noTextRequiresSymbolDiscoveryAndDoesNotAuthorizeByItself() {
        assertNull(policy.resolveCandidate(""))
        assertEquals(
            VisualActionType.NEXT_AD,
            policy.resolveCandidate("", symbolDiscovery = VisualActionType.NEXT_AD)
        )
        assertEquals(
            VisualActionType.CLOSE,
            policy.resolveCandidate("", symbolDiscovery = VisualActionType.CLOSE)
        )
        assertNull(policy.resolveCandidate("", symbolDiscovery = VisualActionType.UNKNOWN))
    }

    @Test
    fun textActionCannotBeOverriddenByConflictingSymbolDiscovery() {
        assertEquals(
            VisualActionType.NEXT_AD,
            policy.resolveCandidate("Next", symbolDiscovery = VisualActionType.CLOSE)
        )
        assertEquals(
            VisualActionType.CLOSE,
            policy.resolveCandidate("Close", symbolDiscovery = VisualActionType.NEXT_AD)
        )
    }

    @Test
    fun onlyTheTwoApprovedActionsCanBeResolved() {
        assertNotNull(policy.resolveCandidate("Next"))
        assertNotNull(policy.resolveCandidate("Close"))
        assertNull(policy.resolveCandidate("something else"))
    }
}
