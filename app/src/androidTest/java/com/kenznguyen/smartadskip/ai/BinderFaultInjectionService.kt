package com.kenznguyen.smartadskip.ai

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import java.util.concurrent.ConcurrentHashMap

/**
 * Instrumentation-only endpoint. Tests explicitly release replies to force Binder races
 * without sleeping or relying on scheduler timing.
 */
class BinderFaultInjectionService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var endpoint: Messenger
    private val heldReplies = ConcurrentHashMap<Int, Message>()

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("binder-fault-injection").apply { start() }
        endpoint = Messenger(Handler(thread.looper) { message ->
            when (message.what) {
                HOLD -> heldReplies[message.arg1] = Message.obtain(message)
                RELEASE -> {
                    val held = heldReplies.remove(message.arg1)
                    if (held != null) reply(held)
                }
                RELEASE_ALL -> heldReplies.keys.toList().forEach { id ->
                    heldReplies.remove(id)?.let(::reply)
                }
                else -> reply(message)
            }
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder = endpoint.binder

    private fun reply(request: Message) {
        val payload = Bundle().apply {
            putInt("request_id", request.arg1)
            putLong("generation", request.data.getLong("generation", -1L))
            putString("session", request.data.getString("session").orEmpty())
        }
        runCatching {
            request.replyTo?.send(Message.obtain(null, RESPONSE, request.arg1, 0).apply {
                data = payload
            })
        }
    }

    override fun onDestroy() {
        heldReplies.clear()
        if (::thread.isInitialized) thread.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val HOLD = 100
        const val RELEASE = 101
        const val RELEASE_ALL = 102
        const val RESPONSE = 103
    }
}
