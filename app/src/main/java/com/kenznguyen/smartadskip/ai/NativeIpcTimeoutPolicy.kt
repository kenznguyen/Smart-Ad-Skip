package com.kenznguyen.smartadskip.ai

/** Timeout budgets and recovery semantics for the isolated-process IPC boundary. */
internal object NativeIpcTimeoutPolicy {
    enum class Request { HELLO, LOAD, INFER }

    const val CONNECTION_TIMEOUT_MILLIS = 10_000L
    const val HELLO_TIMEOUT_MILLIS = 5_000L

    fun timeoutMillis(request: Request, inferenceTimeoutMillis: Long): Long {
        require(inferenceTimeoutMillis > 0L) { "inference_timeout_must_be_positive" }
        return when (request) {
            Request.HELLO -> HELLO_TIMEOUT_MILLIS
            Request.LOAD -> Math.multiplyExact(inferenceTimeoutMillis, 5L)
            Request.INFER -> inferenceTimeoutMillis
        }
    }

    /** HELLO is transport-only; LOAD/INFER may be executing native work remotely. */
    fun terminateProcessOnTimeout(request: Request): Boolean =
        request != Request.HELLO
}
