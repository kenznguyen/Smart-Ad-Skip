package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrActionPolicyTest {
    private val policy = OcrActionPolicy()

    @Test
    fun recognizesNextAndSkipLabelsAsNextAdHints() {
        assertEquals(OcrActionHint.NEXT_AD, policy.analyze("Next"))
        assertEquals(OcrActionHint.NEXT_AD, policy.analyze("Skip Ad"))
    }

    @Test
    fun recognizesCloseAndDismissLabelsAsCloseHints() {
        assertEquals(OcrActionHint.CLOSE, policy.analyze("Close"))
        assertEquals(OcrActionHint.CLOSE, policy.analyze("Dismiss"))
    }

    @Test
    fun marksProtectedCommercialAndRewardLabelsAsProtected() {
        listOf("Install", "Download", "Buy now", "Purchase", "Claim reward", "Collect")
            .forEach { assertEquals(it, OcrActionHint.PROTECTED, policy.analyze(it)) }
    }

    @Test
    fun treatsMissingOrUnrecognizedTextAsNeutralHint() {
        assertEquals(OcrActionHint.UNKNOWN, policy.analyze(""))
        assertEquals(OcrActionHint.UNKNOWN, policy.analyze("limited time offer"))
    }

    @Test
    fun allowsSymbolOnlyVisualActionWhenOcrFindsNoText() {
        assertTrue(policy.canProceed(VisualActionType.NEXT_AD, OcrActionHint.UNKNOWN))
        assertTrue(policy.canProceed(VisualActionType.CLOSE, OcrActionHint.UNKNOWN))
    }

    @Test
    fun rejectsOcrLabelThatConflictsWithVisualAction() {
        assertFalse(policy.canProceed(VisualActionType.NEXT_AD, OcrActionHint.CLOSE))
        assertFalse(policy.canProceed(VisualActionType.CLOSE, OcrActionHint.NEXT_AD))
    }

    @Test
    fun protectedOcrTextAlwaysVetoesVisualAction() {
        assertFalse(policy.canProceed(VisualActionType.NEXT_AD, OcrActionHint.PROTECTED))
        assertFalse(policy.canProceed(VisualActionType.CLOSE, OcrActionHint.PROTECTED))
    }

    @Test
    fun ocrNeverAuthorizesUnknownVisualAction() {
        assertFalse(policy.canProceed(VisualActionType.UNKNOWN, OcrActionHint.NEXT_AD))
        assertFalse(policy.canProceed(VisualActionType.UNKNOWN, OcrActionHint.CLOSE))
        assertFalse(policy.canProceed(VisualActionType.UNKNOWN, OcrActionHint.UNKNOWN))
    }
}
