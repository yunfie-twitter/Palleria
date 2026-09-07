package com.discord.oauth2rpc.utils

class ActivityFlags(
    bitfield: Long = 0L,
) : BitField(bitfield) {
    companion object {
        const val INSTANCE = 1L shl 0
        const val JOIN = 1L shl 1
        const val SPECTATE = 1L shl 2
        const val JOIN_REQUEST = 1L shl 3
        const val SYNC = 1L shl 4
        const val PLAY = 1L shl 5
        const val PARTY_PRIVACY_FRIENDS = 1L shl 6
        const val PARTY_PRIVACY_VOICE_CHANNEL = 1L shl 7
        const val EMBEDDED = 1L shl 8
    }
}
