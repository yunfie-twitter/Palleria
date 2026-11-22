package com.discord.oauth2rpc

import org.json.JSONObject

data class GatewayPacket(
    val op: Int,
    val d: Any?,
    val s: Int? = null,
    val t: String? = null,
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("op", op)
        if (d != null) {
            json.put("d", d)
        } else {
            json.put("d", JSONObject.NULL)
        }
        if (s != null) json.put("s", s)
        if (t != null) json.put("t", t)
        return json
    }
}

data class User(
    val id: String,
    val username: String,
    val discriminator: String? = null,
    val globalName: String? = null,
    val avatar: String? = null,
    val bot: Boolean = false,
    val flags: Int? = null,
)

data class ReadyEvent(
    val version: Int,
    val user: User,
    val sessionType: String? = null,
    val sessionId: String? = null,
    val resumeGatewayUrl: String? = null,
)

data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val refreshToken: String?,
    val scope: String?,
)

enum class GatewayConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    IDENTIFYING,
    READY,
    RESUMING,
    CLOSING,
    CLOSED,
    ERROR,
}
