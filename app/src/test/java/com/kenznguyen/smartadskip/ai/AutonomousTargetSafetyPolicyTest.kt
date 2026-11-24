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
        assertFalse(policy.canExecute(target(text = "Claim reward")))
        assertFalse(policy.canExecute(target(description = "Install now")))
        assertFalse(policy.canExecute(target(text = "Learn more")))
        assertFalse(policy.canExecute(target(text = "Continue")))
    }

    @Test fun allowsSymbolOnlyAndSkipCloseTargets() {
        assertTrue(policy.canExecute(target()))
        assertTrue(policy.canExecute(target(text = "Skip")))
        assertTrue(policy.canExecute(target(description = "Close")))
    }
}
