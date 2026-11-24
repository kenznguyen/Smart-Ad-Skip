package com.kenznguyen.smartadskip.ai

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class NativeInferenceBusyException(val diagnostic: String) : IllegalStateException("native_inference_busy $diagnostic")

/** Serializes JNI operations. Caller timeout never releases ownership of synchronous JNI. */
internal class NativeRuntimeLifecycle(
    private val executor: ExecutorService = NativeRuntimeExecutor.executor,
    private val clockNanos: () -> Long = System::nanoTime
) {
    enum class State { NEW, LOADING, READY, FAILED, CLOSING, CLOSED }
    enum class InferenceState { IDLE, RUNNING, CALLER_TIMED_OUT, STUCK }
    data class InferenceSnapshot(val state: InferenceState, val ageMillis: Long)

    private val lock = Any()
    @Volatile private var currentState = State.NEW
    private var closeRequested = false
    private var inferenceInFlight = false
    private var inferenceStartedAtNanos: Long? = null
    private var inferenceCallerTimedOut = false
    private var closeFuture: CompletableFuture<Unit>? = null
    val state: State get() = currentState

    fun inferenceSnapshot(stuckAfterMillis: Long = DEFAULT_STUCK_AFTER_MILLIS): InferenceSnapshot = synchronized(lock) {
        val started = inferenceStartedAtNanos
        if (!inferenceInFlight || started == null) InferenceSnapshot(InferenceState.IDLE, 0L)
        else {
            val age = ((clockNanos() - started) / 1_000_000L).coerceAtLeast(0L)
            val status = when {
                age >= stuckAfterMillis -> InferenceState.STUCK
                inferenceCallerTimedOut -> InferenceState.CALLER_TIMED_OUT
                else -> InferenceState.RUNNING
            }
            InferenceSnapshot(status, age)
        }
    }

    fun markInferenceCallerTimedOut() = synchronized(lock) {
        if (inferenceInFlight) inferenceCallerTimedOut = true
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

    fun <T> infer(nativeInference: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        synchronized(lock) {
            check(currentState == State.READY && !closeRequested) { "native_runtime_not_ready" }
            if (inferenceInFlight) {
                val s = inferenceSnapshot()
                throw NativeInferenceBusyException("state=${s.state.name.lowercase()} age_ms=${s.ageMillis}")
            }
            inferenceInFlight = true
            inferenceStartedAtNanos = clockNanos()
            inferenceCallerTimedOut = false
            try {
                executor.execute {
                    var value: T? = null
                    var failure: Throwable? = null
                    try {
                        checkNotClosing()
                        value = nativeInference()
                        checkNotClosing()
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        synchronized(lock) {
                            inferenceInFlight = false
                            inferenceStartedAtNanos = null
                            inferenceCallerTimedOut = false
                        }
                    }
                    if (failure == null) {
                        @Suppress("UNCHECKED_CAST")
                        future.complete(value as T)
                    } else future.completeExceptionally(failure)
                }
            } catch (error: Throwable) {
                inferenceInFlight = false
                inferenceStartedAtNanos = null
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
            try { nativeClose() } catch (error: Throwable) { failure = error }
            finally { synchronized(lock) { currentState = State.CLOSED } }
            if (failure == null) future.complete(Unit) else future.completeExceptionally(failure)
        }
        future
    }

    private fun checkNotClosing() = synchronized(lock) {
        if (closeRequested) throw CancellationException("native_runtime_closing")
    }

    companion object { const val DEFAULT_STUCK_AFTER_MILLIS = 30_000L }
}

private object NativeRuntimeExecutor {
    val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "qwen3vl-native-lifecycle").apply { isDaemon = true }
    }
}
