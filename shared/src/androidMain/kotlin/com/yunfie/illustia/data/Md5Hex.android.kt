package com.yunfie.illustia.data

import java.security.MessageDigest

internal actual fun md5Hex(input: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(input.encodeToByteArray())
    return digest.joinToString("") { byte -> ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1) }
}
