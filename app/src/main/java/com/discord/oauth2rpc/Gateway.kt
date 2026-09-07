package com.discord.oauth2rpc

import android.util.Log
import com.discord.oauth2rpc.structures.Activity
import com.discord.oauth2rpc.structures.RichPresence
import com.discord.oauth2rpc.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Gateway(
    private val token: String,
    private val okHttpClient: OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private var webSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var lastSequence: Int? = null
    private var heartbeatAckReceived = true
    private val isClosedManually = AtomicBoolean(false)
    private var pendingPresence: RichPresence? = null

    private val _connectionState = MutableStateFlow(GatewayConnectionState.DISCONNECTED)
    val connectionState: StateFlow<GatewayConnectionState> = _connectionState.asStateFlow()

    private val _readyEvent = MutableStateFlow<ReadyEvent?>(null)
    val readyEvent: StateFlow<ReadyEvent?> = _readyEvent.asStateFlow()

    var onReady: ((ReadyEvent) -> Unit)? = null
    var onClose: ((code: Int, reason: String) -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onStateChange: ((GatewayConnectionState) -> Unit)? = null

    fun isRunning(): Boolean {
        val state = _connectionState.value
        return state == GatewayConnectionState.CONNECTED ||
            state == GatewayConnectionState.IDENTIFYING ||
            state == GatewayConnectionState.READY ||
            state == GatewayConnectionState.RESUMING
    }

    fun isReady(): Boolean = _connectionState.value == GatewayConnectionState.READY

    fun connect(initialPresence: RichPresence? = null) {
        if (isRunning()) return
        isClosedManually.set(false)
        pendingPresence = initialPresence
        updateState(GatewayConnectionState.CONNECTING)

        val request =
            Request
                .Builder()
                .url(Constants.GATEWAY_URL)
                .build()

        webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    private fun createWebSocketListener(): WebSocketListener =
        object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response,
            ) {
                updateState(GatewayConnectionState.CONNECTED)
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String,
            ) {
                handleMessage(text)
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                updateState(GatewayConnectionState.CLOSING)
                webSocket.close(code, reason)
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String,
            ) {
                heartbeatJob?.cancel()
                updateState(GatewayConnectionState.CLOSED)
                onClose?.invoke(code, reason)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?,
            ) {
                heartbeatJob?.cancel()
                updateState(GatewayConnectionState.ERROR)
                onError?.invoke(t)
            }
        }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val op = json.optInt("op", -1)
            if (json.has("s") && !json.isNull("s")) {
                lastSequence = json.getInt("s")
            }
            val t = json.optString("t").takeIf { it.isNotBlank() }
            val d = json.opt("d")

            when (op) {
                Constants.Opcode.HELLO -> {
                    val helloData = d as? JSONObject
                    val heartbeatInterval = helloData?.optLong("heartbeat_interval", 41250L) ?: 41250L
                    startHeartbeat(heartbeatInterval)
                    identify()
                }

                Constants.Opcode.HEARTBEAT_ACK -> {
                    heartbeatAckReceived = true
                }

                Constants.Opcode.HEARTBEAT -> {
                    sendHeartbeat()
                }

                Constants.Opcode.RECONNECT -> {
                    reconnect()
                }

                Constants.Opcode.INVALID_SESSION -> {
                    val resumable = (d as? Boolean) ?: false
                    if (!resumable) {
                        identify()
                    }
                }

                Constants.Opcode.DISPATCH -> {
                    if (t == "READY" && d is JSONObject) {
                        handleReady(d)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing gateway message: ${e.message}", e)
        }
    }

    private fun identify() {
        updateState(GatewayConnectionState.IDENTIFYING)
        val identifyPayload =
            JSONObject().apply {
                put("op", Constants.Opcode.IDENTIFY)
                val data =
                    JSONObject().apply {
                        put("token", token)
                        put("capabilities", 125)
                        val properties =
                            JSONObject().apply {
                                put("os", "Android")
                                put("browser", "Discord Android")
                                put("device", "Palleria")
                                put("system_locale", "ja_JP")
                                put("client_version", "214.15")
                                put("os_version", "34")
                            }
                        put("properties", properties)
                        put("compress", false)
                        put(
                            "client_state",
                            JSONObject().apply {
                                put("guild_versions", JSONObject())
                            },
                        )
                        pendingPresence?.let { presence ->
                            val presenceObj =
                                JSONObject().apply {
                                    put("since", presence.since)
                                    val activitiesArr = JSONArray()
                                    presence.activities.forEach { activitiesArr.put(it.toJSONObject()) }
                                    put("activities", activitiesArr)
                                    put("status", presence.status)
                                    put("afk", presence.afk)
                                }
                            put("presence", presenceObj)
                        }
                    }
                put("d", data)
            }
        send(identifyPayload.toString())
    }

    private fun handleReady(d: JSONObject) {
        val userObj = d.optJSONObject("user")
        val user =
            if (userObj != null) {
                User(
                    id = userObj.optString("id", ""),
                    username = userObj.optString("username", ""),
                    discriminator = userObj.optString("discriminator").takeIf { it.isNotBlank() },
                    globalName = userObj.optString("global_name").takeIf { it.isNotBlank() },
                    avatar = userObj.optString("avatar").takeIf { it.isNotBlank() },
                    bot = userObj.optBoolean("bot", false),
                    flags = userObj.optInt("flags", 0),
                )
            } else {
                User(id = "", username = "")
            }

        val event =
            ReadyEvent(
                version = d.optInt("v", Constants.GATEWAY_VERSION),
                user = user,
                sessionType = d.optString("session_type").takeIf { it.isNotBlank() },
                sessionId = d.optString("session_id").takeIf { it.isNotBlank() },
                resumeGatewayUrl = d.optString("resume_gateway_url").takeIf { it.isNotBlank() },
            )
        _readyEvent.value = event
        updateState(GatewayConnectionState.READY)
        onReady?.invoke(event)

        pendingPresence?.let {
            setPresence(it)
        }
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatAckReceived = true
        heartbeatJob =
            scope.launch {
                val initialDelay = (intervalMs * Math.random()).toLong()
                delay(initialDelay)
                while (isActive) {
                    if (!heartbeatAckReceived) {
                        Log.w(TAG, "Heartbeat ACK not received, reconnecting...")
                        reconnect()
                        break
                    }
                    heartbeatAckReceived = false
                    sendHeartbeat()
                    delay(intervalMs)
                }
            }
    }

    private fun sendHeartbeat() {
        val json =
            JSONObject().apply {
                put("op", Constants.Opcode.HEARTBEAT)
                if (lastSequence != null) {
                    put("d", lastSequence)
                } else {
                    put("d", JSONObject.NULL)
                }
            }
        send(json.toString())
    }

    fun setPresence(presence: RichPresence): Boolean {
        pendingPresence = presence
        val payload = presence.toPayload()
        return send(payload.toString())
    }

    fun setActivity(
        activity: Activity,
        status: String = Constants.Status.ONLINE,
        since: Long = System.currentTimeMillis(),
        afk: Boolean = false,
    ): Boolean {
        val presence =
            RichPresence(
                activities = listOf(activity),
                status = status,
                since = since,
                afk = afk,
            )
        return setPresence(presence)
    }

    fun send(text: String): Boolean {
        val ws = webSocket ?: return false
        return ws.send(text)
    }

    fun reconnect() {
        disconnect()
        connect(pendingPresence)
    }

    fun disconnect() {
        isClosedManually.set(true)
        heartbeatJob?.cancel()
        heartbeatJob = null
        try {
            webSocket?.close(1000, "Normal Closure")
        } catch (ignored: Exception) {
        }
        webSocket = null
        updateState(GatewayConnectionState.DISCONNECTED)
    }

    fun close() {
        disconnect()
    }

    private fun updateState(newState: GatewayConnectionState) {
        _connectionState.value = newState
        onStateChange?.invoke(newState)
    }

    companion object {
        private const val TAG = "DiscordGateway"
    }
}
