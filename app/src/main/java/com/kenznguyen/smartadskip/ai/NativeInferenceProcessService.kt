package com.kenznguyen.smartadskip.ai

import android.app.Service
import com.kenznguyen.smartadskip.BuildConfig
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Owns every llama.cpp/JNI object in the dedicated :qwen_native process. */
class NativeInferenceProcessService : Service() {
    private lateinit var worker: HandlerThread
    private lateinit var messenger: Messenger
    private var runtime: LlamaCppQwen3VlRuntime? = null
    // Debug-only deterministic fault injection; never enabled in release builds.
    private var instrumentationHangFrameId: Long? = null
    private var instrumentationObserver: Messenger? = null
    private var instrumentationLifecycle: NativeRuntimeLifecycle? = null
    private val processSessionId = UUID.randomUUID().toString()

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("qwen-native-ipc").apply { start() }
        messenger = Messenger(Handler(worker.looper) { message ->
            handle(message)
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handle(message: Message) {
        val request = message.data
        val response = Bundle().apply {
            putInt(KEY_PID, Process.myPid())
            putString(KEY_PROCESS_SESSION, processSessionId)
            putLong(KEY_GENERATION, request.getLong(KEY_GENERATION, -1L))
        }
        try {
            when (message.what) {
                MSG_HELLO -> {
                    response.putString(KEY_STATUS, STATUS_OK)
                    response.putString(KEY_NATIVE_STATE, "CONNECTED")
                }
                MSG_LOAD -> {
                    runtime?.close()
                    runtime = null
                    val enableInstrumentationFault = BuildConfig.DEBUG &&
                        applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
                        request.getBoolean(KEY_TEST_MODE, false)
                    if (enableInstrumentationFault) {
                        instrumentationHangFrameId = request.getLong(KEY_TEST_HANG_FRAME_ID)
                        instrumentationObserver = request.getParcelable(KEY_TEST_OBSERVER) as? Messenger
                        instrumentationLifecycle = NativeRuntimeLifecycle().also {
                            it.load { }.get(5, TimeUnit.SECONDS)
                        }
                    } else {
                        instrumentationHangFrameId = null
                        instrumentationObserver = null
                        instrumentationLifecycle = null
                        val config = Qwen3VlRuntimeConfig(
                            cpuThreads = request.getInt(KEY_THREADS),
                            contextSize = request.getInt(KEY_CONTEXT),
                            batchSize = request.getInt(KEY_BATCH),
                            maxImageEdge = request.getInt(KEY_MAX_EDGE),
                            inferenceTimeoutMillis = request.getLong(KEY_TIMEOUT)
                        )
                        val next = LlamaCppQwen3VlRuntime(config)
                        runBlocking {
                            next.load(
                                File(request.getString(KEY_MODEL) ?: error("model_path_missing")),
                                File(request.getString(KEY_PROJECTOR) ?: error("projector_path_missing"))
                            )
                        }
                        runtime = next
                    }
                    response.putString(KEY_STATUS, STATUS_OK)
                    response.putString(KEY_NATIVE_STATE, "READY")
                }
                MSG_INFER -> {
                    val frameId = request.getLong(KEY_FRAME_ID)
                    val hangFrameId = instrumentationHangFrameId
                    if (BuildConfig.DEBUG && hangFrameId != null) {
                        val lifecycle = checkNotNull(instrumentationLifecycle) {
                            "instrumentation_native_lifecycle_missing"
                        }
                        val output = lifecycle.infer(frameId) {
                            if (frameId == hangFrameId) {
                                runCatching {
                                    instrumentationObserver?.send(
                                        Message.obtain(null, MSG_TEST_INFERENCE_STARTED).apply {
                                            data = Bundle().apply { putLong(KEY_FRAME_ID, frameId) }
                                        }
                                    )
                                }
                                // Simulate a synchronous JNI call that never returns. The real
                                // NativeRuntimeLifecycle keeps ownership reserved until return;
                                // the only recovery is killing this isolated process.
                                CountDownLatch(1).await()
                            }
                            Qwen3VlOutput(
                                action = AdAction.WAIT,
                                confidence = 1.0f,
                                rationale = "test_frame_id=$frameId"
                            )
                        }.get()
                        response.putString(KEY_STATUS, STATUS_OK)
                        response.putString(KEY_ACTION, output.action.name)
                        response.putString(KEY_TARGET, output.targetElementId)
                        response.putFloat(KEY_CONFIDENCE, output.confidence)
                        response.putString(KEY_RATIONALE, output.rationale)
                        response.putString(KEY_NATIVE_STATE, "READY")
                    } else {
                        val active = runtime ?: error("native_runtime_not_loaded")
                        val elements = request.getParcelableArrayList<Bundle>(KEY_ELEMENTS).orEmpty().map { item ->
                            AdUiElement(
                                id = item.getString(KEY_ID).orEmpty(),
                                bounds = com.kenznguyen.smartadskip.accessibility.ScreenBounds(
                                    item.getInt(KEY_LEFT), item.getInt(KEY_TOP),
                                    item.getInt(KEY_RIGHT), item.getInt(KEY_BOTTOM)
                                ),
                                description = item.getString(KEY_DESCRIPTION),
                                clickable = item.getBoolean(KEY_CLICKABLE)
                            )
                        }
                        val context = AdFrameContext(
                            packageName = request.getString(KEY_PACKAGE).orEmpty(),
                            observedAtEpochMillis = request.getLong(KEY_OBSERVED),
                            screenWidth = request.getInt(KEY_WIDTH),
                            screenHeight = request.getInt(KEY_HEIGHT),
                            imageJpeg = request.getByteArray(KEY_IMAGE) ?: error("image_missing"),
                            elements = elements,
                            frameId = frameId
                        )
                        val output = runBlocking { active.infer(context) }
                        response.putString(KEY_STATUS, STATUS_OK)
                        response.putString(KEY_ACTION, output.action.name)
                        response.putString(KEY_TARGET, output.targetElementId)
                        response.putFloat(KEY_CONFIDENCE, output.confidence)
                        response.putString(KEY_RATIONALE, output.rationale)
                        response.putString(KEY_NATIVE_STATE, "READY")
                    }
                }
                MSG_CLOSE -> {
                    runtime?.close()
                    runtime = null
                    instrumentationLifecycle?.close { }
                    instrumentationLifecycle = null
                    response.putString(KEY_STATUS, STATUS_OK)
                    response.putString(KEY_NATIVE_STATE, "STOPPED")
                }
                else -> error("unknown_native_process_message")
            }
        } catch (error: Throwable) {
            response.putString(KEY_STATUS, STATUS_ERROR)
            response.putString(KEY_ERROR, error.javaClass.simpleName + ":" + (error.message ?: "native_error").take(160))
            response.putString(KEY_NATIVE_STATE, "FAILED")
        }
        runCatching {
            message.replyTo?.send(Message.obtain(null, message.what, message.arg1, 0).apply { data = response })
        }
    }

    override fun onDestroy() {
        // Normal shutdown only. A hung JNI call is recovered by killing this process.
        runCatching { runtime?.close() }
        runtime = null
        instrumentationLifecycle?.close { }
        instrumentationLifecycle = null
        if (::worker.isInitialized) worker.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val MSG_HELLO = 0
        const val MSG_LOAD = 1
        const val MSG_INFER = 2
        const val MSG_CLOSE = 3
        const val MSG_TEST_INFERENCE_STARTED = 900
        const val KEY_TEST_MODE = "instrumentation_test_mode"
        const val KEY_TEST_HANG_FRAME_ID = "instrumentation_hang_frame_id"
        const val KEY_TEST_OBSERVER = "instrumentation_test_observer"
        const val STATUS_OK = "ok"
        const val STATUS_ERROR = "error"
        const val KEY_STATUS = "status"
        const val KEY_ERROR = "error"
        const val KEY_PID = "pid"
        const val KEY_PROCESS_SESSION = "process_session"
        const val KEY_GENERATION = "binding_generation"
        const val KEY_NATIVE_STATE = "native_state"
        const val KEY_THREADS = "threads"
        const val KEY_CONTEXT = "context"
        const val KEY_BATCH = "batch"
        const val KEY_MAX_EDGE = "max_edge"
        const val KEY_TIMEOUT = "timeout"
        const val KEY_MODEL = "model"
        const val KEY_PROJECTOR = "projector"
        const val KEY_PACKAGE = "package"
        const val KEY_OBSERVED = "observed"
        const val KEY_WIDTH = "width"
        const val KEY_HEIGHT = "height"
        const val KEY_IMAGE = "image"
        const val KEY_FRAME_ID = "frame_id"
        const val KEY_ELEMENTS = "elements"
        const val KEY_ID = "id"
        const val KEY_LEFT = "left"
        const val KEY_TOP = "top"
        const val KEY_RIGHT = "right"
        const val KEY_BOTTOM = "bottom"
        const val KEY_DESCRIPTION = "description"
        const val KEY_CLICKABLE = "clickable"
        const val KEY_ACTION = "action"
        const val KEY_TARGET = "target"
        const val KEY_CONFIDENCE = "confidence"
        const val KEY_RATIONALE = "rationale"
    }
}
