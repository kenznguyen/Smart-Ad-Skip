package com.kenznguyen.smartadskip.ai

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Serializes every JNI lifecycle operation. Inference is single-flight; close is
 * non-blocking and queued behind any native call already executing. Queued inference
 * observes the close request and is discarded before entering JNI.
 */
internal class NativeRuntimeLifecycle(
    private val executor: ExecutorService = NativeRuntimeExecutor.executor
) {
    enum class State { NEW, LOADING, READY, FAILED, CLOSING, CLOSED }

    private val lock = Any()
    @Volatile private var currentState = State.NEW
    private var closeRequested = false
    private var inferenceInFlight = false
    private var closeFuture: CompletableFuture<Unit>? = null

    val state: State get() = currentState

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
                    synchronized(lock) {
                        if (!closeRequested) currentState = State.FAILED
                    }
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
            check(!inferenceInFlight) { "native_inference_busy" }
            inferenceInFlight = true
            executor.execute {
                try {
                    checkNotClosing()
                    val result = nativeInference()
                    checkNotClosing()
                    future.complete(result)
                } catch (error: Throwable) {
                    future.completeExceptionally(error)
                } finally {
                    synchronized(lock) { inferenceInFlight = false }
                }
            }
        }
        return future
    }

    /** Marks the runtime unavailable immediately; native destruction happens on its executor. */
    fun close(nativeClose: () -> Unit): CompletableFuture<Unit> = synchronized(lock) {
        closeFuture?.let { return@synchronized it }
        closeRequested = true
        currentState = State.CLOSING
        val future = CompletableFuture<Unit>()
        closeFuture = future
        executor.execute {
            try {
                nativeClose()
                future.complete(Unit)
            } catch (error: Throwable) {
                future.completeExceptionally(error)
            } finally {
                synchronized(lock) { currentState = State.CLOSED }
            }
        }
        future
    }

    private fun checkNotClosing() {
        synchronized(lock) {
            if (closeRequested) throw CancellationException("native_runtime_closing")
        }
    }
}

private object NativeRuntimeExecutor {
    val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "qwen3vl-native-lifecycle").apply { isDaemon = true }
    }
}
