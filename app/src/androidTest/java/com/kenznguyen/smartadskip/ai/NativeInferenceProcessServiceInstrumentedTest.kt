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
import android.os.Process
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real isolated Android Service and Binder transport without loading model files. */
@RunWith(AndroidJUnit4::class)
class NativeInferenceProcessServiceInstrumentedTest {
    @Test
    fun binderDeathReconnectCreatesANewProcessSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val callbacks = ServiceEvents()
        val connection = callbacks.connection
        val intent = Intent(context, NativeInferenceProcessService::class.java)

        try {
            assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            val firstRemote = callbacks.connected.poll(15, TimeUnit.SECONDS)
            assertNotNull("initial isolated service connection timed out", firstRemote)

            val firstHandshake = callbacks.hello(firstRemote!!, 101)
            val firstPid = firstHandshake.getInt(NativeInferenceProcessService.KEY_PID, -1)
            val firstSession = firstHandshake.getString(NativeInferenceProcessService.KEY_PROCESS_SESSION)
            assertTrue("service must run outside instrumentation process", firstPid > 0 && firstPid != Process.myPid())
            assertNotNull("HELLO must return process session identity", firstSession)
            assertTrue(firstSession!!.isNotBlank())

            // Simulate the process-death event that the production watchdog causes after
            // deciding the isolated native process is irrecoverably stuck.
            Process.killProcess(firstPid)
            assertTrue("Binder death callback was not delivered", callbacks.disconnected.poll(15, TimeUnit.SECONDS))

            val reconnected = callbacks.connected.poll(20, TimeUnit.SECONDS)
            assertNotNull("Android did not reconnect the bound service", reconnected)
            val secondHandshake = callbacks.hello(reconnected!!, 102)
            val secondPid = secondHandshake.getInt(NativeInferenceProcessService.KEY_PID, -1)
            val secondSession = secondHandshake.getString(NativeInferenceProcessService.KEY_PROCESS_SESSION)

            assertTrue(secondPid > 0 && secondPid != Process.myPid())
            assertNotNull(secondSession)
            assertNotEquals("restarted process must have a new session UUID", firstSession, secondSession)
            assertNotEquals("restarted process should have a new PID", firstPid, secondPid)
        } finally {
            runCatching { context.unbindService(connection) }
        }
    }

    private class ServiceEvents {
        val connected = LinkedBlockingQueue<Messenger>()
        val disconnected = LinkedBlockingQueue<Boolean>()
        private val responses = LinkedBlockingQueue<Bundle>()
        private val replyMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            responses.offer(message.data)
            true
        })

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null) connected.offer(Messenger(binder))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                disconnected.offer(true)
            }

            override fun onBindingDied(name: ComponentName?) {
                disconnected.offer(true)
            }
        }

        fun hello(remote: Messenger, requestId: Int): Bundle {
            val data = Bundle().apply {
                putLong(NativeInferenceProcessService.KEY_GENERATION, requestId.toLong())
            }
            remote.send(Message.obtain(null, NativeInferenceProcessService.MSG_HELLO, requestId, 0).apply {
                this.data = data
                replyTo = replyMessenger
            })
            val response = responses.poll(10, TimeUnit.SECONDS)
            checkNotNull(response) { "HELLO response timed out" }
            check(response.getLong(NativeInferenceProcessService.KEY_GENERATION, -1L) == requestId.toLong()) {
                "service response did not preserve request binding generation"
            }
            return response
        }
    }
}
