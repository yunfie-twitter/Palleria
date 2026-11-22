package com.discord.oauth2rpc.utils

class GatewayCapabilities(
    bitfield: Long = 0L,
) : BitField(bitfield) {
    companion object {
        const val LAZY_USER_NOTES = 1L shl 0
        const val NO_CALL_CONNECT = 1L shl 1
        const val COMPACT_MEMBER_ARRAY = 1L shl 2
        const val SYNC_CALL_CONNECT = 1L shl 3
        const val DONT_PUSH_TO_CLIENT = 1L shl 4
        const val PULL_STATUS_CHANGE_FROM_CLIENT = 1L shl 5
        const val CLIENT_TRACK_STATUS = 1L shl 6
    }
}
