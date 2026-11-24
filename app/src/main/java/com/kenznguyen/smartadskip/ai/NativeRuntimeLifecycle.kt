package com.kenznguyen.smartadskip.ai

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class NativeInferenceBusyException(val diagnostic: String = "state=running age_ms=0") :
    IllegalStateException("native_inference_busy $diagnostic")

/**
 * Serializes JNI operations. A timed-out caller never releases synchronous JNI ownership.
 * A STUCK call quarantines this runtime; recovery is possible only after that same call returns,
 * or by terminating and recreating the process that owns the native runtime.
 */
internal class NativeRuntimeLifecycle(
    private val executor: ExecutorService = NativeRuntimeExecutor.executor,
    private val clockNanos: () -> Long = System::nanoTime,
    private val eventLogger: (String) -> Unit = { android.util.Log.d(LOG_TAG, it) }
) {
    enum class State { NEW, LOADING, READY, FAILED, CLOSING, CLOSED }
    enum class InferenceState { IDLE, RUNNING, CALLER_TIMED_OUT, STUCK }

    data class InferenceSnapshot(
        val state: InferenceState,
        val ageMillis: Long,
        val requestId: Long? = null,
        val threadId: Long? = null
    )

    private val lock = Any()
    @Volatile private var currentState = State.NEW
    private var closeRequested = false
    // Reservation is acquired before enqueue; nativeStartedAtNanos is set only on worker entry.
    private var inferenceInFlight = false
    private var nativeStartedAtNanos: Long? = null
    private var inferenceRequestId: Long? = null
    private var inferenceThreadId: Long? = null
    private var inferenceCallerTimedOut = false
    private var stuckEventEmitted = false
    private var closeFuture: CompletableFuture<Unit>? = null

    val state: State get() = currentState

    fun inferenceSnapshot(stuckAfterMillis: Long = DEFAULT_STUCK_AFTER_MILLIS): InferenceSnapshot =
        synchronized(lock) {
            if (!inferenceInFlight) return@synchronized InferenceSnapshot(InferenceState.IDLE, 0L)
            val started = nativeStartedAtNanos
            val age = if (started == null) 0L
                else ((clockNanos() - started) / 1_000_000L).coerceAtLeast(0L)
            val status = when {
                started != null && age >= stuckAfterMillis -> InferenceState.STUCK
                inferenceCallerTimedOut -> InferenceState.CALLER_TIMED_OUT
                else -> InferenceState.RUNNING
            }
            if (status == InferenceState.STUCK && !stuckEventEmitted) {
                stuckEventEmitted = true
                emit("inference_stuck request_id=${inferenceRequestId ?: -1L} thread_id=${inferenceThreadId ?: -1L} native_elapsed_ms=$age")
            }
            InferenceSnapshot(status, age, inferenceRequestId, inferenceThreadId)
        }

    fun markInferenceCallerTimedOut() = synchronized(lock) {
        if (inferenceInFlight && !inferenceCallerTimedOut) {
            inferenceCallerTimedOut = true
            val age = nativeElapsedMillisLocked()
            emit(
                "inference_caller_timed_out request_id=${inferenceRequestId ?: -1L} " +
                    "thread_id=${inferenceThreadId ?: -1L} native_elapsed_ms=$age"
            )
        }
    }

    fun load(nativeLoad: () -> Unit): CompletableFuture<Unit> {
        val future = CompletableFuture<Unit>()
        synchronized(lock) {
            check(currentState == State.NEW) { "native_runtime_already_started" }
            currentState = State.LOADING
            executor.execute {
                try {
                    checkNotClosing()
                    nativeLoad()
                    synchronized(lock) {
                        if (closeRequested) throw CancellationException("native_runtime_closing")
                        currentState = State.READY
                    }
                    future.complete(Unit)
                } catch (error: Throwable) {
                    synchronized(lock) { if (!closeRequested) currentState = State.FAILED }
                    future.completeExceptionally(error)
                }
            }
        }
        return future
    }

    fun <T> infer(requestId: Long, nativeInference: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        synchronized(lock) {
            check(currentState == State.READY && !closeRequested) { "native_runtime_not_ready" }
            if (inferenceInFlight) {
                val snapshot = inferenceSnapshot()
                throw NativeInferenceBusyException(
                    "state=${snapshot.state.name.lowercase()} age_ms=${snapshot.ageMillis} " +
                        "request_id=${snapshot.requestId ?: -1L} thread_id=${snapshot.threadId ?: -1L}"
                )
            }
            inferenceInFlight = true
            inferenceRequestId = requestId
            nativeStartedAtNanos = null
            inferenceThreadId = null
            inferenceCallerTimedOut = false
            stuckEventEmitted = false
            try {
                executor.execute {
                    val threadId = Thread.currentThread().id
                    val startedAt = clockNanos()
                    synchronized(lock) {
                        nativeStartedAtNanos = startedAt
                        inferenceThreadId = threadId
                    }
                    emit("inference_started request_id=$requestId thread_id=$threadId")
                    var value: T? = null
                    var failure: Throwable? = null
                    try {
                        checkNotClosing()
                        value = nativeInference()
                        checkNotClosing()
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        val elapsed = ((clockNanos() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                        emit(
                            "inference_native_returned request_id=$requestId thread_id=$threadId " +
                                "native_elapsed_ms=$elapsed outcome=${if (failure == null) "success" else "failure"}"
                        )
                        synchronized(lock) {
                            // This is the only normal path that releases native ownership.
                            inferenceInFlight = false
                            nativeStartedAtNanos = null
                            inferenceRequestId = null
                            inferenceThreadId = null
                            inferenceCallerTimedOut = false
                            stuckEventEmitted = false
                        }
                        emit(
                            "inference_ownership_released request_id=$requestId thread_id=$threadId " +
                                "native_elapsed_ms=$elapsed"
                        )
                    }
                    if (failure == null) {
                        @Suppress("UNCHECKED_CAST")
                        future.complete(value as T)
                    } else future.completeExceptionally(failure)
                }
            } catch (error: Throwable) {
                // The task never entered JNI, so no native ownership was acquired.
                inferenceInFlight = false
                nativeStartedAtNanos = null
                inferenceRequestId = null
                inferenceThreadId = null
                inferenceCallerTimedOut = false
                future.completeExceptionally(error)
            }
        }
        return future
    }

    fun close(nativeClose: () -> Unit): CompletableFuture<Unit> = synchronized(lock) {
        closeFuture?.let { return@synchronized it }
        closeRequested = true
        currentState = State.CLOSING
        val future = CompletableFuture<Unit>()
        closeFuture = future
        executor.execute {
            var failure: Throwable? = null
            try {
                nativeClose()
            } catch (error: Throwable) {
                failure = error
            } finally {
                synchronized(lock) { currentState = State.CLOSED }
            }
            if (failure == null) future.complete(Unit) else future.completeExceptionally(failure)
        }
        future
    }

    private fun nativeElapsedMillisLocked(): Long {
        val started = nativeStartedAtNanos ?: return 0L
        return ((clockNanos() - started) / 1_000_000L).coerceAtLeast(0L)
    }

    private fun emit(message: String) {
        runCatching { eventLogger(message) }
    }

    private fun checkNotClosing() = synchronized(lock) {
        if (closeRequested) throw CancellationException("native_runtime_closing")
    }

    companion object {
        const val DEFAULT_STUCK_AFTER_MILLIS = 30_000L
        private const val LOG_TAG = "SmartAdSkipQwen"
    }
}

private object NativeRuntimeExecutor {
    val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "qwen3vl-native-lifecycle").apply { isDaemon = true }
    }
}
