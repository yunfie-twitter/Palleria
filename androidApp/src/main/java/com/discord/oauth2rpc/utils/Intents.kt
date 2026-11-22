package com.discord.oauth2rpc.utils

class Intents(
    bitfield: Long = 0L,
) : BitField(bitfield) {
    companion object {
        const val GUILDS = 1L shl 0
        const val GUILD_MEMBERS = 1L shl 1
        const val GUILD_MODERATION = 1L shl 2
        const val GUILD_EMOJIS_AND_STICKERS = 1L shl 3
        const val GUILD_INTEGRATIONS = 1L shl 4
        const val GUILD_WEBHOOKS = 1L shl 5
        const val GUILD_INVITES = 1L shl 6
        const val GUILD_VOICE_STATES = 1L shl 7
        const val GUILD_PRESENCES = 1L shl 8
        const val GUILD_MESSAGES = 1L shl 9
        const val GUILD_MESSAGE_REACTIONS = 1L shl 10
        const val GUILD_MESSAGE_TYPING = 1L shl 11
        const val DIRECT_MESSAGES = 1L shl 12
        const val DIRECT_MESSAGE_REACTIONS = 1L shl 13
        const val DIRECT_MESSAGE_TYPING = 1L shl 14
        const val MESSAGE_CONTENT = 1L shl 15
        const val GUILD_SCHEDULED_EVENTS = 1L shl 16
        const val AUTO_MODERATION_CONFIGURATION = 1L shl 20
        const val AUTO_MODERATION_EXECUTION = 1L shl 21
    }
}
