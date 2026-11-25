package com.kenznguyen.smartadskip.ai

import com.kenznguyen.smartadskip.accessibility.ScreenBounds
import com.kenznguyen.smartadskip.accessibility.StructuralNodeMetadata
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutonomousTargetSafetyPolicyTest {
    private val policy = AutonomousTargetSafetyPolicy()

    private fun target(text: String? = null, description: String? = null) = StructuralNodeMetadata(
        viewClassName = "android.widget.Button",
        resourceId = null,
        clickable = true,
        bounds = ScreenBounds(10, 10, 100, 100),
        text = text,
        contentDescription = description
    )

    @Test fun blocksProtectedRewardAndInstallTargets() {
        assertFalse(policy.canExecute(target(text = "Claim reward"), AdAction.CLOSE))
        assertFalse(policy.canExecute(target(description = "Install now"), AdAction.CLOSE))
        assertFalse(policy.canExecute(target(text = "Learn more"), AdAction.NEXT_AD))
        assertFalse(policy.canExecute(target(text = "Continue"), AdAction.NEXT_AD))
        assertFalse(policy.canExecute(target(text = "Play now"), AdAction.CONTINUE))
    }

    @Test fun rejectsCompletelyUnlabelledTargets() {
        assertFalse(policy.canExecute(target(), AdAction.CLOSE))
        assertFalse(policy.canExecute(target(text = "  "), AdAction.NEXT_AD))
        assertFalse(policy.canExecute(target(), AdAction.CONTINUE))
    }

    @Test fun allowsOnlyActionSpecificSkipAndCloseLabels() {
        assertTrue(policy.canExecute(target(text = "Skip"), AdAction.NEXT_AD))
        assertTrue(policy.canExecute(target(description = "Close"), AdAction.CLOSE))
        assertTrue(policy.canExecute(target(text = ">>"), AdAction.NEXT_AD))
        assertTrue(policy.canExecute(target(text = "✕"), AdAction.CLOSE))
        assertTrue(policy.canExecute(target(text = "Close ad"), AdAction.CLOSE))
        assertTrue(policy.canExecute(target(text = "Skip ad"), AdAction.NEXT_AD))
        assertFalse(policy.canExecute(target(text = ">>"), AdAction.CLOSE))
        assertFalse(policy.canExecute(target(text = "✕"), AdAction.NEXT_AD))
    }

    @Test fun allowsContinueOnlyForExplicitResumeLabels() {
        assertTrue(policy.canExecute(target(text = "Continue"), AdAction.CONTINUE))
        assertTrue(policy.canExecute(target(description = "Resume"), AdAction.CONTINUE))
        assertTrue(policy.canExecute(target(text = "Play"), AdAction.CONTINUE))
        assertTrue(policy.canExecute(target(text = "Tap to resume"), AdAction.CONTINUE))
        assertFalse(policy.canExecute(target(text = "Play now"), AdAction.CONTINUE))
        assertFalse(policy.canExecute(target(text = "Install"), AdAction.CONTINUE))
        assertFalse(policy.canExecute(target(text = "Continue"), AdAction.NEXT_AD))
        assertFalse(policy.canExecute(target(text = "Continue"), AdAction.CLOSE))
    }
}
