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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real proxy, isolated Android service, process watchdog and Binder reconnect.
 * The debug-only service hook blocks one inference dispatch deterministically; no model files
 * or nondeterministic native timing are required.
 */
@RunWith(AndroidJUnit4::class)
class NativeRuntimeRecoveryInstrumentedTest {
    @Test
    fun timedOutInferenceKillsOldOwnerReconnectsAndAcceptsOnlyNewRequest() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext.applicationContext
        val observer = ProcessObserver(context)
        val model = File(context.cacheDir, "p2-35-test-model.gguf").apply { writeBytes(byteArrayOf(1)) }
        val projector = File(context.cacheDir, "p2-35-test-projector.gguf").apply { writeBytes(byteArrayOf(1)) }
        val runtime = IsolatedQwen3VlRuntime(
            context = context,
            config = Qwen3VlRuntimeConfig(inferenceTimeoutMillis = 1_000L),
            instrumentationHangFrameId = 101L,
            instrumentationObserver = observer.markerMessenger
        )

        try {
            observer.bind()
            val firstBinder = observer.connected.poll(10, TimeUnit.SECONDS)
            assertNotNull("observer failed to bind native service", firstBinder)
            val firstIdentity = observer.hello(firstBinder!!, 1)
            val firstPid = firstIdentity.getInt(NativeInferenceProcessService.KEY_PID, -1)
            val firstSession = firstIdentity.getString(NativeInferenceProcessService.KEY_PROCESS_SESSION)
            assertTrue(firstPid > 0 && firstPid != Process.myPid())
            assertNotNull(firstSession)

            runtime.load(model, projector)

            // Request 101 enters the deliberately blocked service handler and owns its process.
            val timedOutInference = async {
                runCatching { runtime.infer(frame(101L)) }
            }
            assertEquals(101L, observer.hangStarted.poll(10, TimeUnit.SECONDS))

            // This request must remain behind the proxy's single-flight operation mutex.
            val nextInference = async { runtime.infer(frame(202L)) }
            assertTrue("stuck inference did not time out", timedOutInference.await().isFailure)

            assertTrue("old native process did not report Binder death", observer.disconnected.poll(15, TimeUnit.SECONDS))
            val reconnected = observer.connected.poll(20, TimeUnit.SECONDS)
            assertNotNull("Android did not reconnect the native service", reconnected)
            val secondIdentity = observer.hello(reconnected!!, 2)
            val secondPid = secondIdentity.getInt(NativeInferenceProcessService.KEY_PID, -1)
            val secondSession = secondIdentity.getString(NativeInferenceProcessService.KEY_PROCESS_SESSION)

            assertNotEquals("process session must change after watchdog termination", firstSession, secondSession)
            assertTrue(secondPid > 0 && secondPid != Process.myPid())
            assertFalse("old process must no longer own native resources", File("/proc/$firstPid").exists())

            val recovered = withTimeout(20_000L) { nextInference.await() }
            assertEquals(AdAction.WAIT, recovered.action)
            assertEquals("test_frame_id=202", recovered.rationale)
            assertEquals("recovered request must be served by the new process", secondPid, observer.hello(reconnected, 3).getInt(NativeInferenceProcessService.KEY_PID))
        } finally {
            runtime.close()
            observer.unbind()
            model.delete()
            projector.delete()
        }
    }

    private fun frame(id: Long) = AdFrameContext(
        packageName = AutonomousAdDecisionEngine.TARGET_PACKAGE,
        observedAtEpochMillis = System.currentTimeMillis(),
        screenWidth = 1080,
        screenHeight = 1920,
        imageJpeg = byteArrayOf(1),
        elements = emptyList(),
        frameId = id
    )

    private class ProcessObserver(private val context: Context) {
        val connected = LinkedBlockingQueue<Messenger>()
        val disconnected = LinkedBlockingQueue<Boolean>()
        val hangStarted = LinkedBlockingQueue<Long>()
        private val responses = LinkedBlockingQueue<Bundle>()
        val markerMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == NativeInferenceProcessService.MSG_TEST_INFERENCE_STARTED) {
                hangStarted.offer(message.data.getLong(NativeInferenceProcessService.KEY_FRAME_ID))
            } else {
                responses.offer(message.data)
            }
            true
        })
        private val replyTo = Messenger(Handler(Looper.getMainLooper()) { message ->
            responses.offer(message.data)
            true
        })
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null) connected.offer(Messenger(binder))
            }
            override fun onServiceDisconnected(name: ComponentName?) { disconnected.offer(true) }
            override fun onBindingDied(name: ComponentName?) { disconnected.offer(true) }
        }

        fun bind() {
            check(context.bindService(
                Intent(context, NativeInferenceProcessService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )) { "native service bind failed" }
        }

        fun hello(remote: Messenger, id: Int): Bundle {
            remote.send(Message.obtain(null, NativeInferenceProcessService.MSG_HELLO, id, 0).apply {
                data = Bundle().apply { putLong(NativeInferenceProcessService.KEY_GENERATION, id.toLong()) }
                replyTo = replyTo
            })
            return checkNotNull(responses.poll(5, TimeUnit.SECONDS)) { "native HELLO timed out" }
        }

        fun unbind() {
            runCatching { context.unbindService(connection) }
        }
    }
}
