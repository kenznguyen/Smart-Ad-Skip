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
import android.os.SystemClock
import android.os.RemoteException
import com.kenznguyen.smartadskip.accessibility.ScreenBounds
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Main-process proxy. All JNI objects and native threads live in :qwen_native.
 * Cancelling a blocked inference kills that process; it never frees or reuses its JNI slot.
 */
internal class IsolatedQwen3VlRuntime(
    context: Context,
    private val config: Qwen3VlRuntimeConfig
) : Qwen3VlRuntime, AutoCloseable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val operationMutex = Mutex()
    private val requestIds = AtomicInteger(1)
    private val pending = java.util.concurrent.ConcurrentHashMap<Int, kotlinx.coroutines.CancellableContinuation<Bundle>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var remote: Messenger? = null
    @Volatile private var remotePid: Int = -1
    @Volatile private var ready = false
    private var bound = false
    private var closed = false
    private var recoveryDisabled = false
    private var restartCount = 0
    private var lastRestartAtMillis: Long? = null
    @Volatile private var inferenceStartedAtMillis: Long? = null
    private var modelFile: File? = null
    private var projectorFile: File? = null
    private var connectionFuture = CompletableFuture<Messenger>()

    private val clientMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        val continuation = pending.remove(message.arg1)
        val response = message.data
        remotePid = response.getInt(NativeInferenceProcessService.KEY_PID, remotePid)
        if (response.getString(NativeInferenceProcessService.KEY_STATUS) == NativeInferenceProcessService.STATUS_OK) {
            continuation?.takeIf { it.isActive }?.resume(response)
        } else {
            val reason = response.getString(NativeInferenceProcessService.KEY_ERROR) ?: "native_process_request_failed"
            continuation?.takeIf { it.isActive }?.resumeWithException(IllegalStateException(reason))
        }
        true
    })

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) {
            val connected = Messenger(binder ?: return)
            synchronized(lock) {
                remote = connected
                ready = false
                if (!connectionFuture.isDone) connectionFuture.complete(connected)
            }
            AiRuntimeDashboardStatus.setNativeProcessState("PROCESS_CONNECTED")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // A crashed remote service remains bound; Android may reconnect this binding.
            handleRemoteLoss("native_process_disconnected", unbind = false, allowRecovery = true)
        }

        override fun onBindingDied(name: ComponentName?) {
            // Android requires an explicit unbind before this ServiceConnection can bind again.
            handleRemoteLoss("native_process_binding_died", unbind = true, allowRecovery = true)
        }

        override fun onNullBinding(name: ComponentName?) {
            // A null binder is a service contract/configuration failure, not a process crash.
            handleRemoteLoss("native_process_null_binding", unbind = true, allowRecovery = false)
        }
    }

    /**
     * Invalidates every artifact tied to the lost Binder endpoint. A binding-death callback
     * must unbind before the same ServiceConnection can be used again; an ordinary process
     * disconnect deliberately keeps the binding so Android can reconnect it automatically.
     */
    private fun handleRemoteLoss(reason: String, unbind: Boolean, allowRecovery: Boolean) {
        if (closed) {
            synchronized(lock) {
                remote = null
                remotePid = -1
                ready = false
                if (connectionFuture.isDone) connectionFuture = CompletableFuture()
                if (unbind && bound) {
                    runCatching { appContext.unbindService(connection) }
                    bound = false
                }
            }
            failPending("native_process_proxy_closed")
            return
        }

        val now = SystemClock.elapsedRealtime()
        val action = if (allowRecovery) {
            NativeProcessRecoveryPolicy.evaluate(
                processAlive = false,
                inferenceAgeMillis = 0L,
                restartCount = restartCount,
                nowMillis = now,
                lastRestartAtMillis = lastRestartAtMillis
            )
        } else NativeProcessRecoveryPolicy.Action.FAIL_CLOSED

        synchronized(lock) {
            remote = null
            remotePid = -1
            ready = false
            if (connectionFuture.isDone) connectionFuture = CompletableFuture()
            if (unbind && bound) {
                runCatching { appContext.unbindService(connection) }
                bound = false
            }
            if (action == NativeProcessRecoveryPolicy.Action.RESTART_PROCESS) {
                restartCount += 1
                lastRestartAtMillis = now
            } else {
                recoveryDisabled = true
                if (bound && action == NativeProcessRecoveryPolicy.Action.FAIL_CLOSED) {
                    runCatching { appContext.unbindService(connection) }
                    bound = false
                }
            }
        }
        failPending(reason)
        AiRuntimeDashboardStatus.setNativeProcessState(
            if (action == NativeProcessRecoveryPolicy.Action.RESTART_PROCESS) "PROCESS_REBIND_PENDING"
            else "PROCESS_RECOVERY_LIMIT_REACHED"
        )
        android.util.Log.w("SmartAdSkipQwen", "native_process_binding_lost reason=$reason action=$action unbound=$unbind")
    }

    override val isModelReady: Boolean get() = ready && !closed

    suspend fun load(model: File, projector: File) = operationMutex.withLock {
        require(model.isFile && projector.isFile) { "qwen_model_files_missing" }
        modelFile = model
        projectorFile = projector
        ensureBound()
        loadRemote()
    }

    override suspend fun infer(context: AdFrameContext): Qwen3VlOutput = operationMutex.withLock {
        check(!closed) { "native_process_proxy_closed" }
        check(!recoveryDisabled) { "native_process_recovery_budget_exhausted" }
        if (!ready) {
            ensureBound()
            loadRemote()
        }
        val bundle = Bundle().apply {
            putString(NativeInferenceProcessService.KEY_PACKAGE, context.packageName)
            putLong(NativeInferenceProcessService.KEY_OBSERVED, context.observedAtEpochMillis)
            putInt(NativeInferenceProcessService.KEY_WIDTH, context.screenWidth)
            putInt(NativeInferenceProcessService.KEY_HEIGHT, context.screenHeight)
            putByteArray(NativeInferenceProcessService.KEY_IMAGE, context.imageJpeg)
            putLong(NativeInferenceProcessService.KEY_FRAME_ID, context.frameId)
            val encoded = ArrayList<Bundle>(context.elements.size)
            context.elements.forEach { element ->
                encoded += Bundle().apply {
                    putString(NativeInferenceProcessService.KEY_ID, element.id)
                    putInt(NativeInferenceProcessService.KEY_LEFT, element.bounds.left)
                    putInt(NativeInferenceProcessService.KEY_TOP, element.bounds.top)
                    putInt(NativeInferenceProcessService.KEY_RIGHT, element.bounds.right)
                    putInt(NativeInferenceProcessService.KEY_BOTTOM, element.bounds.bottom)
                    putString(NativeInferenceProcessService.KEY_DESCRIPTION, element.description)
                    putBoolean(NativeInferenceProcessService.KEY_CLICKABLE, element.clickable)
                }
            }
            putParcelableArrayList(NativeInferenceProcessService.KEY_ELEMENTS, encoded)
        }
        inferenceStartedAtMillis = SystemClock.elapsedRealtime()
        val response = try {
            // Caller timeout does not release native ownership. The watchdog may terminate
            // the isolated process only after native-duration policy says it is stuck.
            withTimeout(config.inferenceTimeoutMillis) {
                request(NativeInferenceProcessService.MSG_INFER, bundle, killProcessOnCancel = true)
            }
        } finally {
            inferenceStartedAtMillis = null
        }
        val action = runCatching {
            AdAction.valueOf(response.getString(NativeInferenceProcessService.KEY_ACTION).orEmpty())
        }.getOrElse { throw IllegalArgumentException("model_action_invalid", it) }
        Qwen3VlOutput(
            action = action,
            targetElementId = response.getString(NativeInferenceProcessService.KEY_TARGET),
            confidence = response.getFloat(NativeInferenceProcessService.KEY_CONFIDENCE),
            rationale = response.getString(NativeInferenceProcessService.KEY_RATIONALE)
        )
    }

    private suspend fun loadRemote() {
        val model = modelFile ?: throw IllegalStateException("qwen_model_path_missing")
        val projector = projectorFile ?: throw IllegalStateException("qwen_projector_path_missing")
        val bundle = Bundle().apply {
            putString(NativeInferenceProcessService.KEY_MODEL, model.absolutePath)
            putString(NativeInferenceProcessService.KEY_PROJECTOR, projector.absolutePath)
            putInt(NativeInferenceProcessService.KEY_THREADS, config.cpuThreads)
            putInt(NativeInferenceProcessService.KEY_CONTEXT, config.contextSize)
            putInt(NativeInferenceProcessService.KEY_BATCH, config.batchSize)
            putInt(NativeInferenceProcessService.KEY_MAX_EDGE, config.maxImageEdge)
            putLong(NativeInferenceProcessService.KEY_TIMEOUT, config.inferenceTimeoutMillis)
        }
        withTimeout(config.inferenceTimeoutMillis * 5L) {
            request(NativeInferenceProcessService.MSG_LOAD, bundle, killProcessOnCancel = true)
        }
        ready = true
        AiRuntimeDashboardStatus.setNativeProcessState("PROCESS_READY")
    }

    private suspend fun ensureBound() {
        check(!closed) { "native_process_proxy_closed" }
        check(!recoveryDisabled) { "native_process_recovery_budget_exhausted" }
        val alreadyConnected = synchronized(lock) {
            if (!bound) {
                val intent = Intent(appContext, NativeInferenceProcessService::class.java)
                check(appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                    "native_process_bind_failed"
                }
                bound = true
            }
            remote != null
        }
        if (!alreadyConnected) {
            try {
                withTimeout(CONNECTION_TIMEOUT_MILLIS) { connectionFuture.awaitResult() }
            } catch (timeout: TimeoutCancellationException) {
                handleRemoteLoss(
                    reason = "native_process_connection_timeout",
                    unbind = true,
                    allowRecovery = true
                )
                throw IllegalStateException("native_process_connection_timeout", timeout)
            }
        }
        if (remotePid <= 0) {
            try {
                withTimeout(REQUEST_TIMEOUT_MILLIS) {
                    request(NativeInferenceProcessService.MSG_HELLO, Bundle())
                }
            } catch (timeout: TimeoutCancellationException) {
                handleRemoteLoss(
                    reason = "native_process_hello_timeout",
                    unbind = true,
                    allowRecovery = true
                )
                throw IllegalStateException("native_process_hello_timeout", timeout)
            }
        }
    }

    private suspend fun request(
        what: Int,
        data: Bundle,
        killProcessOnCancel: Boolean = false
    ): Bundle = suspendCancellableCoroutine { continuation ->
        val id = requestIds.getAndIncrement()
        pending[id] = continuation
        continuation.invokeOnCancellation {
            pending.remove(id)
            if (killProcessOnCancel) terminateRemoteProcess("inference_cancelled_or_timed_out")
        }
        val target = remote
        if (target == null) {
            pending.remove(id)
            continuation.resumeWithException(IllegalStateException("native_process_not_connected"))
            return@suspendCancellableCoroutine
        }
        try {
            target.send(Message.obtain(null, what, id, 0).apply {
                this.data = data
                replyTo = clientMessenger
            })
        } catch (error: RemoteException) {
            pending.remove(id)
            handleRemoteLoss(
                reason = "native_process_send_failed",
                unbind = true,
                allowRecovery = true
            )
            continuation.resumeWithException(error)
        }
    }

    private fun terminateRemoteProcess(reason: String) {
        val pid = remotePid
        val now = SystemClock.elapsedRealtime()
        val age = inferenceStartedAtMillis?.let { (now - it).coerceAtLeast(0L) } ?: config.inferenceTimeoutMillis
        val action = NativeProcessRecoveryPolicy.evaluate(
            processAlive = pid > 0,
            inferenceAgeMillis = age,
            restartCount = restartCount,
            nowMillis = now,
            lastRestartAtMillis = lastRestartAtMillis,
            stuckAfterMillis = config.inferenceTimeoutMillis
        )
        ready = false
        if (action == NativeProcessRecoveryPolicy.Action.TERMINATE_PROCESS && pid != Process.myPid()) {
            // Kill only the dedicated same-UID process; never clear the in-flight JNI ownership locally.
            AiRuntimeDashboardStatus.setNativeProcessState("PROCESS_TERMINATING")
            synchronized(lock) {
                remote = null
                remotePid = -1
                if (connectionFuture.isDone) connectionFuture = CompletableFuture()
            }
            Process.killProcess(pid)
            AiRuntimeDashboardStatus.setNativeProcessState("PROCESS_KILLED")
        } else {
            recoveryDisabled = action == NativeProcessRecoveryPolicy.Action.FAIL_CLOSED ||
                pid <= 0 || pid == Process.myPid()
            AiRuntimeDashboardStatus.setNativeProcessState("PROCESS_RECOVERY_FAIL_CLOSED")
        }
        android.util.Log.w(
            "SmartAdSkipQwen",
            "native_process_watchdog action=$action reason=$reason pid=$pid native_elapsed_ms=$age restart_count=$restartCount"
        )
    }

    private fun failPending(reason: String) {
        pending.entries.toList().forEach { (id, continuation) ->
            if (pending.remove(id, continuation) && continuation.isActive) {
                continuation.resumeWithException(IllegalStateException(reason))
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        ready = false
        val target = remote
        if (target != null) {
            runCatching {
                target.send(Message.obtain(null, NativeInferenceProcessService.MSG_CLOSE).apply {
                    replyTo = clientMessenger
                })
            }
        }
        synchronized(lock) {
            if (bound) {
                runCatching { appContext.unbindService(connection) }
                bound = false
            }
            remote = null
        }
        failPending("native_process_proxy_closed")
    }

    private suspend fun CompletableFuture<Messenger>.awaitResult(): Messenger =
        suspendCancellableCoroutine { continuation ->
            // The future belongs to the binding, not to an individual waiter. Cancelling
            // one timed-out waiter must never cancel the shared connection result.
            whenComplete { value, failure ->
                if (continuation.isActive) {
                    if (failure != null) continuation.resumeWithException(failure.cause ?: failure)
                    else if (value != null) continuation.resume(value)
                    else continuation.resumeWithException(IllegalStateException("native_process_empty_connection"))
                }
            }
        }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 10_000L
        const val REQUEST_TIMEOUT_MILLIS = 5_000L
    }
}
