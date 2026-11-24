package com.kenznguyen.smartadskip.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic Binder scheduling tests. No Thread.sleep-based race assumptions. */
@RunWith(AndroidJUnit4::class)
class BinderFaultInjectionInstrumentedTest {
    @Test
    fun delayedOldGenerationReplyIsRejectedAfterReconnect() {
        withEndpoint { endpoint, replies ->
            val oldGeneration = 41L
            send(endpoint, BinderFaultInjectionService.HOLD, 1, oldGeneration)
            // A reconnect advances the binding generation before the old reply is released.
            val currentGeneration = 42L
            send(endpoint, BinderFaultInjectionService.RELEASE, 1, oldGeneration)
            val stale = replies.poll(5, TimeUnit.SECONDS)
            assertNotNull("held Binder response was not released", stale)
            assertFalse(
                "late callback from old binding must not be accepted",
                NativeProcessSessionPolicy.isCurrentGeneration(
                    stale!!.getLong("generation"), currentGeneration
                )
            )
        }
    }

    @Test
    fun timeoutCanRaceReconnectWithoutCancellingNewGeneration() {
        withEndpoint { endpoint, replies ->
            val oldGeneration = 70L
            send(endpoint, BinderFaultInjectionService.HOLD, 7, oldGeneration)

            // Model the caller timing out while the service still owns its queued reply.
            val timedOut = replies.poll(100, TimeUnit.MILLISECONDS)
            assertEquals(null, timedOut)

            val newGeneration = 71L
            send(endpoint, BinderFaultInjectionService.RELEASE, 7, oldGeneration)
            val late = replies.poll(5, TimeUnit.SECONDS)
            assertNotNull(late)
            assertFalse(
                NativeProcessSessionPolicy.isCurrentGeneration(
                    late!!.getLong("generation"), newGeneration
                )
            )

            send(endpoint, 8, 8, newGeneration)
            val current = replies.poll(5, TimeUnit.SECONDS)
            assertNotNull("new binding request was affected by old timeout", current)
            assertEquals(newGeneration, current!!.getLong("generation"))
        }
    }

    @Test
    fun oldWatchdogCannotAuthorizeTerminationAfterReconnect() {
        val oldGeneration = 90L
        val newGeneration = 91L
        val oldPid = 1234
        val oldSession = "session-old"
        val oldStartTicks = 900L

        assertFalse(
            NativeProcessSessionPolicy.mayTerminate(
                expectedGeneration = oldGeneration,
                currentGeneration = newGeneration,
                expectedPid = oldPid,
                currentPid = oldPid,
                expectedSession = oldSession,
                currentSession = oldSession,
                expectedStartTicks = oldStartTicks,
                currentStartTicks = oldStartTicks
            )
        )
        assertTrue(
            NativeProcessSessionPolicy.mayTerminate(
                expectedGeneration = newGeneration,
                currentGeneration = newGeneration,
                expectedPid = 5678,
                currentPid = 5678,
                expectedSession = "session-new",
                currentSession = "session-new",
                expectedStartTicks = 910L,
                currentStartTicks = 910L
            )
        )
    }

    private fun withEndpoint(block: (Messenger, LinkedBlockingQueue<Bundle>) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.context
        val connected = LinkedBlockingQueue<Messenger>()
        val replies = LinkedBlockingQueue<Bundle>()
        val callback = Messenger(Handler(Looper.getMainLooper()) { message ->
            replies.offer(message.data)
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null) connected.offer(Messenger(binder))
            }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val intent = Intent().setComponent(
            ComponentName(context, BinderFaultInjectionService::class.java)
        )
        try {
            assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            val endpoint = connected.poll(10, TimeUnit.SECONDS)
            assertNotNull("fault endpoint connection timed out", endpoint)
            activeReplyTo = callback
            block(endpoint!!, replies)
        } finally {
            activeReplyTo = null
            runCatching { context.unbindService(connection) }
        }
    }

    private var activeReplyTo: Messenger? = null

    private fun send(endpoint: Messenger, what: Int, requestId: Int, generation: Long) {
        endpoint.send(Message.obtain(null, what, requestId, 0).apply {
            data = Bundle().apply { putLong("generation", generation) }
            replyTo = checkNotNull(activeReplyTo)
        })
    }
}
