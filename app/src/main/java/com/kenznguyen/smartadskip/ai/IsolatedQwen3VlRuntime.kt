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
import kotlinx.coroutines.withTimeoutOrNull
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
    private data class PendingRequest(val generation: Long, val handshake: Boolean, val continuation: kotlinx.coroutines.CancellableContinuation<Bundle>)
    private val pending = java.util.concurrent.ConcurrentHashMap<Int, PendingRequest>()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var remote: Messenger? = null
    @Volatile private var remotePid: Int = -1
    @Volatile private var processSessionId: String? = null
    @Volatile private var bindingGeneration: Long = 0L
    @Volatile private var ready = false
    private var connection: ServiceConnection? = null
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
        val pendingRequest = pending.remove(message.arg1)
        if (pendingRequest != null) {
            val response = message.data
            val responseGeneration = response.getLong(NativeInferenceProcessService.KEY_GENERATION, -1L)
            val responsePid = response.getInt(NativeInferenceProcessService.KEY_PID, -1)
            val responseSession = response.getString(NativeInferenceProcessService.KEY_PROCESS_SESSION)
            val valid = synchronized(lock) {
                if (pendingRequest.generation != bindingGeneration || responseGeneration != bindingGeneration) false
                else if (pendingRequest.handshake) {
                    val accepted = NativeProcessSessionPolicy.isValidHandshake(
                        responseGeneration, bindingGeneration, responsePid, Process.myPid()
                    ) && !responseSession.isNullOrBlank()
                    if (accepted) {
                        remotePid = responsePid
                        processSessionId = responseSession
                    }
                    accepted
                } else {
                    NativeProcessSessionPolicy.isValidHandshake(
                        responseGeneration, bindingGeneration, responsePid, Process.myPid()
                    ) && responsePid == remotePid &&
                        NativeProcessSessionPolicy.isSameProcess(processSessionId, responseSession)
                }
            }
            val continuation = pendingRequest.continuation
            if (!valid) continuation.takeIf { it.isActive }?.resumeWithException(
                IllegalStateException("native_process_stale_or_invalid_response")
            )
            else if (response.getString(NativeInferenceProcessService.KEY_STATUS) == NativeInferenceProcessService.STATUS_OK)
                continuation.takeIf { it.isActive }?.resume(response)
            else continuation.takeIf { it.isActive }?.resumeWithException(
                IllegalStateException(response.getString(NativeInferenceProcessService.KEY_ERROR) ?: "native_process_request_failed")
            )
        }
        true
    })

    private fun createConnection(generation: Long): ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) {
            if (binder == null) {
                handleRemoteLoss("native_process_null_binder", generation, true, false)
                return
            }
            val connected = Messenger(binder)
            val accepted = synchronized(lock) {
                if (closed || generation != bindingGeneration) false
                else {
                    remote = connected
                    remotePid = -1
                    processSessionId = null
                    ready = false
                    if (!connectionFuture.isDone) connectionFuture.complete(connected)
                    true
                }
            }
            if (accepted) AiRuntimeDashboardStatus.setNativeProcessState("PROCESS_CONNECTED")
        }
        override fun onServiceDisconnected(name: ComponentName?) =
            handleRemoteLoss("native_process_disconnected", generation, false, true)
        override fun onBindingDied(name: ComponentName?) =
            handleRemoteLoss("native_process_binding_died", generation, true, true)
        override fun onNullBinding(name: ComponentName?) =
            handleRemoteLoss("native_process_null_binding", generation, true, false)
    }

    /**
     * Invalidates every artifact tied to the lost Binder endpoint. A binding-death callback
     * must unbind before the same ServiceConnection can be used again; an ordinary process
     * disconnect deliberately keeps the binding so Android can reconnect it automatically.
     */
    private fun handleRemoteLoss(reason: String, generation: Long, unbind: Boolean, allowRecovery: Boolean) {
        val activeConnection = synchronized(lock) {
            if (generation != bindingGeneration) return
            connection
        }
        if (closed) {
            synchronized(lock) {
                remote = null
                remotePid = -1
                processSessionId = null
                ready = false
                if (connectionFuture.isDone) connectionFuture = CompletableFuture()
                if (unbind && bound) {
                    runCatching { activeConnection?.let { appContext.unbindService(it) } }
                    bound = false
                    connection = null
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
            processSessionId = null
            ready = false
            if (connectionFuture.isDone) connectionFuture = CompletableFuture()
            if (unbind && bound) {
                runCatching { activeConnection?.let { appContext.unbindService(it) } }
                bound = false
                connection = null
            }
            if (action == NativeProcessRecoveryPolicy.Action.RESTART_PROCESS) {
                restartCount += 1
                lastRestartAtMillis = now
            } else {
                recoveryDisabled = true
                if (bound && action == NativeProcessRecoveryPolicy.Action.FAIL_CLOSED) {
                    activeConnection?.let { runCatching { appContext.unbindService(it) } }
                    bound = false
                    connection = null
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
            withTimeout(NativeIpcTimeoutPolicy.timeoutMillis(NativeIpcTimeoutPolicy.Request.INFER, config.inferenceTimeoutMillis)) {
                request(
                    NativeInferenceProcessService.MSG_INFER,
                    bundle,
                    killProcessOnCancel = NativeIpcTimeoutPolicy.terminateProcessOnTimeout(NativeIpcTimeoutPolicy.Request.INFER)
                )
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
        val requestGeneration = bindingGeneration
        val response = withTimeout(NativeIpcTimeoutPolicy.timeoutMillis(NativeIpcTimeoutPolicy.Request.LOAD, config.inferenceTimeoutMillis)) {
            request(
                NativeInferenceProcessService.MSG_LOAD,
                bundle,
                killProcessOnCancel = NativeIpcTimeoutPolicy.terminateProcessOnTimeout(NativeIpcTimeoutPolicy.Request.LOAD)
            )
        }
        val becameReady = synchronized(lock) {
            val responseSession = response.getString(NativeInferenceProcessService.KEY_PROCESS_SESSION)
            if (requestGeneration != bindingGeneration || remote == null ||
                response.getInt(NativeInferenceProcessService.KEY_PID, -1) != remotePid ||
                !NativeProcessSessionPolicy.isSameProcess(processSessionId, responseSession)
            ) false
            else {
                ready = true
                true
            }
        }
        check(becameReady) { "native_process_session_changed_during_load" }
        AiRuntimeDashboardStatus.setNativeProcessState("PROCESS_READY")
    }

    private suspend fun ensureBound() {
        check(!closed) { "native_process_proxy_closed" }
        check(!recoveryDisabled) { "native_process_recovery_budget_exhausted" }
        val alreadyConnected = synchronized(lock) {
            if (!bound) {
                bindingGeneration += 1L
                connectionFuture = CompletableFuture()
                val nextConnection = createConnection(bindingGeneration)
                connection = nextConnection
                val intent = Intent(appContext, NativeInferenceProcessService::class.java)
                check(appContext.bindService(intent, nextConnection, Context.BIND_AUTO_CREATE)) {
                    connection = null
                    "native_process_bind_failed"
                }
                bound = true
            }
            remote != null
        }
        if (!alreadyConnected) {
            val connected = withTimeoutOrNull(NativeIpcTimeoutPolicy.CONNECTION_TIMEOUT_MILLIS) {
                connectionFuture.awaitResult()
            }
            if (connected == null) {
                handleRemoteLoss(
                    reason = "native_process_connection_timeout",
                    generation = bindingGeneration,
                    unbind = true,
                    allowRecovery = true
                )
                throw IllegalStateException("native_process_connection_timeout")
            }
        }
        if (remotePid <= 0 || processSessionId.isNullOrBlank()) {
            val hello = withTimeoutOrNull(
                NativeIpcTimeoutPolicy.timeoutMillis(NativeIpcTimeoutPolicy.Request.HELLO, config.inferenceTimeoutMillis)
            ) {
                request(
                    NativeInferenceProcessService.MSG_HELLO,
                    Bundle(),
                    killProcessOnCancel = NativeIpcTimeoutPolicy.terminateProcessOnTimeout(NativeIpcTimeoutPolicy.Request.HELLO)
                )
            }
            if (hello == null) {
                handleRemoteLoss(
                    reason = "native_process_hello_timeout",
                    generation = bindingGeneration,
                    unbind = true,
                    allowRecovery = true
                )
                throw IllegalStateException("native_process_hello_timeout")
            }
        }
    }

    private suspend fun request(
        what: Int,
        data: Bundle,
        killProcessOnCancel: Boolean = false
    ): Bundle = suspendCancellableCoroutine { continuation ->
        val id = requestIds.getAndIncrement()
        val generation = bindingGeneration
        val isHandshake = what == NativeInferenceProcessService.MSG_HELLO
        data.putLong(NativeInferenceProcessService.KEY_GENERATION, generation)
        pending[id] = PendingRequest(generation, isHandshake, continuation)
        continuation.invokeOnCancellation {
            pending.remove(id)
            if (killProcessOnCancel) terminateRemoteProcess("inference_cancelled_or_timed_out")
        }
        val target = synchronized(lock) { if (generation == bindingGeneration) remote else null }
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
                generation = generation,
                unbind = true,
                allowRecovery = true
            )
            continuation.resumeWithException(error)
        }
    }

    private fun terminateRemoteProcess(reason: String) {
        val identity = synchronized(lock) { Triple(bindingGeneration, remotePid, processSessionId) }
        val generation = identity.first
        val pid = identity.second
        val sessionId = identity.third
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
        synchronized(lock) { ready = false }
        if (action == NativeProcessRecoveryPolicy.Action.TERMINATE_PROCESS && pid != Process.myPid()) {
            // Kill only the dedicated same-UID process; never clear the in-flight JNI ownership locally.
            AiRuntimeDashboardStatus.setNativeProcessState("PROCESS_TERMINATING")
            val terminated = synchronized(lock) {
                if (!NativeProcessSessionPolicy.mayTerminate(
                        generation, bindingGeneration, pid, remotePid, sessionId, processSessionId
                    )
                ) {
                    false
                } else {
                    // Serialize reconnect state changes with the PID kill decision.
                    remote = null
                    remotePid = -1
                    processSessionId = null
                    ready = false
                    if (connectionFuture.isDone) connectionFuture = CompletableFuture()
                    Process.killProcess(pid)
                    true
                }
            }
            if (!terminated) {
                android.util.Log.w("SmartAdSkipQwen", "native_process_watchdog_skip_stale_identity generation=$generation pid=$pid")
                return
            }
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
            if (pending.remove(id, continuation) && continuation.continuation.isActive) {
                continuation.continuation.resumeWithException(IllegalStateException(reason))
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
                connection?.let { runCatching { appContext.unbindService(it) } }
                bound = false
            }
            connection = null
            bindingGeneration += 1L
            remote = null
            remotePid = -1
            processSessionId = null
            ready = false
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

}
