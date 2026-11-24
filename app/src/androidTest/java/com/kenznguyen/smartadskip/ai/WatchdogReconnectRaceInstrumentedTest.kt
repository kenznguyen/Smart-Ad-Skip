package com.kenznguyen.smartadskip.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real isolated-process kill/reconnect with a deliberately delayed stale watchdog decision. */
@RunWith(AndroidJUnit4::class)
class WatchdogReconnectRaceInstrumentedTest {
    @Test
    fun staleWatchdogCannotKillReconnectedNativeProcess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val callbacks = Callbacks()
        val connection = callbacks.connection
        val intent = Intent(context, NativeInferenceProcessService::class.java)
        val generation = AtomicLong(1L)
        val watchdogCaptured = CountDownLatch(1)
        val allowWatchdogToContinue = CountDownLatch(1)
        val watchdogResult = LinkedBlockingQueue<Boolean>()
        var firstPid = -1

        try {
            assertTrue(context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            val first = callbacks.connected.poll(15, TimeUnit.SECONDS)
            assertNotNull("initial Binder connection timed out", first)
            val firstHello = callbacks.hello(first!!, 201)
            firstPid = firstHello.getInt(NativeInferenceProcessService.KEY_PID, -1)
            val firstSession = firstHello.getString(NativeInferenceProcessService.KEY_PROCESS_SESSION)
            val firstStartTicks = processStartTicks(firstPid)
            assertTrue(firstPid > 0 && firstPid != Process.myPid())
            assertNotNull(firstSession)
            assertNotNull(firstStartTicks)

            val watchdog = Thread {
                // Snapshot exactly the identity held by the old watchdog before reconnect.
                val expectedGeneration = generation.get()
                val expectedPid = firstPid
                val expectedSession = firstSession
                val expectedStartTicks = firstStartTicks
                watchdogCaptured.countDown()
                check(allowWatchdogToContinue.await(15, TimeUnit.SECONDS))
                val mayKill = NativeProcessSessionPolicy.mayTerminate(
                    expectedGeneration = expectedGeneration,
                    currentGeneration = generation.get(),
                    expectedPid = expectedPid,
                    currentPid = callbacks.currentPid,
                    expectedSession = expectedSession,
                    currentSession = callbacks.currentSession,
                    expectedStartTicks = expectedStartTicks,
                    currentStartTicks = callbacks.currentStartTicks
                )
                if (mayKill) Process.killProcess(expectedPid)
                watchdogResult.offer(mayKill)
            }
            watchdog.start()
            assertTrue(watchdogCaptured.await(5, TimeUnit.SECONDS))

            // Simulate a watchdog that wins the first half of the race and terminates the
            // stuck process. Android must reconnect the still-bound ServiceConnection.
            Process.killProcess(firstPid)
            assertTrue("Binder death callback missing", callbacks.disconnected.poll(15, TimeUnit.SECONDS))
            val second = callbacks.connected.poll(20, TimeUnit.SECONDS)
            assertNotNull("Android failed to reconnect after process death", second)
            val secondHello = callbacks.hello(second!!, 202)
            generation.incrementAndGet()
            callbacks.currentPid = secondHello.getInt(NativeInferenceProcessService.KEY_PID, -1)
            callbacks.currentSession = secondHello.getString(NativeInferenceProcessService.KEY_PROCESS_SESSION)
            callbacks.currentStartTicks = processStartTicks(callbacks.currentPid)

            assertNotEquals(firstSession, callbacks.currentSession)
            assertTrue(callbacks.currentPid > 0 && callbacks.currentPid != Process.myPid())
            assertNotNull(callbacks.currentStartTicks)

            // Release the old watchdog only after the new Binder session is fully established.
            allowWatchdogToContinue.countDown()
            assertEquals(false, watchdogResult.poll(5, TimeUnit.SECONDS))
            watchdog.join(5_000)

            // Prove the new process remains alive and serves another request.
            val finalHello = callbacks.hello(second, 203)
            assertEquals(callbacks.currentPid, finalHello.getInt(NativeInferenceProcessService.KEY_PID))
            assertEquals(callbacks.currentSession, finalHello.getString(NativeInferenceProcessService.KEY_PROCESS_SESSION))
        } finally {
            allowWatchdogToContinue.countDown()
            runCatching { context.unbindService(connection) }
        }
    }

    private fun processStartTicks(pid: Int): Long? {
        if (pid <= 0) return null
        val stat = runCatching { File("/proc/$pid/stat").readText() }.getOrNull() ?: return null
        val close = stat.lastIndexOf(')')
        if (close < 0 || close + 2 >= stat.length) return null
        // /proc/<pid>/stat field 22; fields after the comm field start at field 3.
        return stat.substring(close + 2).trim().split(Regex("\\s+"))
            .getOrNull(19)?.toLongOrNull()
    }

    private class Callbacks {
        val connected = LinkedBlockingQueue<Messenger>()
        val disconnected = LinkedBlockingQueue<Boolean>()
        private val responses = LinkedBlockingQueue<Bundle>()
        @Volatile var currentPid: Int = -1
        @Volatile var currentSession: String? = null
        @Volatile var currentStartTicks: Long? = null
        private val replyTo = Messenger(Handler(Looper.getMainLooper()) { message ->
            responses.offer(message.data)
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null) connected.offer(Messenger(binder))
            }
            override fun onServiceDisconnected(name: ComponentName?) { disconnected.offer(true) }
            override fun onBindingDied(name: ComponentName?) { disconnected.offer(true) }
        }

        fun hello(remote: Messenger, requestId: Int): Bundle {
            remote.send(Message.obtain(null, NativeInferenceProcessService.MSG_HELLO, requestId, 0).apply {
                data = Bundle().apply {
                    putLong(NativeInferenceProcessService.KEY_GENERATION, requestId.toLong())
                }
                replyTo = this@Callbacks.replyTo
            })
            val response = responses.poll(10, TimeUnit.SECONDS)
            checkNotNull(response) { "HELLO response timed out" }
            assertEquals(requestId.toLong(), response.getLong(NativeInferenceProcessService.KEY_GENERATION))
            return response
        }
    }
}
