package com.kenznguyen.smartadskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestFrameCoalescerTest {
    @Test fun keepsOnlyTheMostRecentFrame() {
        val mailbox = LatestFrameCoalescer<Int>()

        mailbox.offer(1)
        mailbox.offer(2)
        mailbox.offer(3)

        assertTrue(mailbox.hasPending())
        assertEquals(3, mailbox.poll())
        assertFalse(mailbox.hasPending())
        assertNull(mailbox.poll())
    }

    @Test fun clearDropsPendingFrameAcrossLifecycleReset() {
        val mailbox = LatestFrameCoalescer<String>()
        mailbox.offer("old")

        mailbox.clear()

        assertFalse(mailbox.hasPending())
        assertNull(mailbox.poll())
    }
}
