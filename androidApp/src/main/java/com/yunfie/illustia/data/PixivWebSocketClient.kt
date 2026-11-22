package com.yunfie.illustia.data

import com.yunfie.illustia.models.PixivSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface PixivWebSocketState {
    data object Idle : PixivWebSocketState

    data object Connecting : PixivWebSocketState

    data class Connected(
        val sinceMillis: Long,
    ) : PixivWebSocketState

    data class Reconnecting(
        val attempt: Int,
        val delayMillis: Long,
    ) : PixivWebSocketState

    data class Failed(
        val cause: Throwable,
    ) : PixivWebSocketState

    data object Closed : PixivWebSocketState
}

sealed interface PixivWebSocketMessage {
    data class Text(
        val value: String,
    ) : PixivWebSocketMessage

    data class Binary(
        val value: ByteString,
    ) : PixivWebSocketMessage
}

class PixivWebSocketClient internal constructor(
    baseClient: OkHttpClient,
    private val url: String,
    private val accessToken: () -> String,
    private val extraHeaders: Map<String, String> = emptyMap(),
    private val maxReconnectDelayMillis: Long = 30_000,
) : AutoCloseable {
    private val client = baseClient.newBuilder().pingInterval(30, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socket = AtomicReference<WebSocket?>()
    private val manuallyClosed = AtomicBoolean(false)
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private val _state = MutableStateFlow<PixivWebSocketState>(PixivWebSocketState.Idle)
    private val _messages = MutableSharedFlow<PixivWebSocketMessage>(extraBufferCapacity = 64)

    val state: StateFlow<PixivWebSocketState> = _state.asStateFlow()
    val messages: SharedFlow<PixivWebSocketMessage> = _messages.asSharedFlow()

    fun connect() {
        if (socket.get() != null || _state.value is PixivWebSocketState.Connecting) return
        manuallyClosed.set(false)
        _state.value = PixivWebSocketState.Connecting
        val request =
            Request
                .Builder()
                .url(url)
                .header("Authorization", "Bearer ${accessToken()}")
                .apply { extraHeaders.forEach { (name, value) -> header(name, value) } }
                .build()
        socket.set(client.newWebSocket(request, listener))
    }

    fun send(text: String): Boolean = socket.get()?.send(text) == true

    fun send(bytes: ByteString): Boolean = socket.get()?.send(bytes) == true

    fun disconnect(
        code: Int = 1000,
        reason: String = "Client closed",
    ) {
        manuallyClosed.set(true)
        reconnectJob?.cancel()
        socket.getAndSet(null)?.close(code, reason)
        _state.value = PixivWebSocketState.Closed
        scope.cancel()
    }

    override fun close() {
        disconnect()
    }

    private fun reconnect(cause: Throwable) {
        socket.set(null)
        if (manuallyClosed.get()) return
        reconnectJob?.cancel()
        val attempt = ++reconnectAttempt
        val delayMillis = minOf(1_000L * (1L shl minOf(attempt - 1, 5)), maxReconnectDelayMillis)
        _state.value = PixivWebSocketState.Reconnecting(attempt, delayMillis)
        reconnectJob =
            scope.launch {
                delay(delayMillis)
                connect()
            }
    }

    private val listener =
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                reconnectAttempt = 0
                socket.set(webSocket)
                _state.value = PixivWebSocketState.Connected(System.currentTimeMillis())
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                _messages.tryEmit(PixivWebSocketMessage.Text(text))
            }

            override fun onMessage(
                webSocket: WebSocket,
                bytes: ByteString,
            ) {
                _messages.tryEmit(PixivWebSocketMessage.Binary(bytes))
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                socket.compareAndSet(webSocket, null)
                if (!manuallyClosed.get()) reconnect(IllegalStateException("WebSocket closed: $code $reason"))
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                socket.compareAndSet(webSocket, null)
                _state.value = PixivWebSocketState.Failed(t)
                reconnect(t)
            }
        }
}
