package com.discord.oauth2rpc.utils

object Constants {
    const val GATEWAY_VERSION = 10
    const val GATEWAY_URL = "wss://gateway.discord.gg/?v=$GATEWAY_VERSION&encoding=json"
    const val API_BASE_URL = "https://discord.com/api/v$GATEWAY_VERSION"
    const val CDN_BASE_URL = "https://cdn.discordapp.com"

    object Opcode {
        const val DISPATCH = 0
        const val HEARTBEAT = 1
        const val IDENTIFY = 2
        const val PRESENCE_UPDATE = 3
        const val VOICE_STATE_UPDATE = 4
        const val RESUME = 6
        const val RECONNECT = 7
        const val REQUEST_GUILD_MEMBERS = 8
        const val INVALID_SESSION = 9
        const val HELLO = 10
        const val HEARTBEAT_ACK = 11
    }

    object ActivityType {
        const val PLAYING = 0
        const val STREAMING = 1
        const val LISTENING = 2
        const val WATCHING = 3
        const val CUSTOM = 4
        const val COMPETING = 5
    }

    object Status {
        const val ONLINE = "online"
        const val DND = "dnd"
        const val IDLE = "idle"
        const val INVISIBLE = "invisible"
        const val OFFLINE = "offline"
    }

    object CloseCode {
        const val UNKNOWN_ERROR = 4000
        const val UNKNOWN_OPCODE = 4001
        const val DECODE_ERROR = 4002
        const val NOT_AUTHENTICATED = 4003
        const val AUTHENTICATION_FAILED = 4004
        const val ALREADY_AUTHENTICATED = 4005
        const val INVALID_SEQ = 4007
        const val RATE_LIMITED = 4008
        const val SESSION_TIMED_OUT = 4009
        const val INVALID_SHARD = 4010
        const val SHARDING_REQUIRED = 4011
        const val INVALID_API_VERSION = 4012
        const val INVALID_INTENTS = 4013
        const val DISALLOWED_INTENTS = 4014
    }
}
