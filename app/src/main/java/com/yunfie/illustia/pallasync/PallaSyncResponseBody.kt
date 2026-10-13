package com.yunfie.illustia.pallasync

import okhttp3.Response
import okio.Buffer

private const val MAX_RESPONSE_BYTES = 8_388_608L
private const val READ_CHUNK_BYTES = 8192L

/** Cap decoded bytes while streaming, including gzip, before constructing strings (§10.16). */
internal fun Response.pallaSyncBytes(maximum: Long = MAX_RESPONSE_BYTES): ByteArray {
    require(maximum in 1..MAX_RESPONSE_BYTES) { "C_SCHEMA_VIOLATION" }
    val responseBody = body ?: error("C_SCHEMA_VIOLATION")
    val source = responseBody.source()
    val buffer = Buffer()
    while (true) {
        val count = source.read(buffer, minOf(READ_CHUNK_BYTES, maximum + 1 - buffer.size))
        if (count == -1L) return buffer.readByteArray()
        check(buffer.size <= maximum) { "C_LOCAL_LIMIT" }
    }
}

internal fun Response.pallaSyncText(): String {
    val bytes = pallaSyncBytes()
    // Native validators also enforce UTF-8 for signed bodies. Never use replacement decoding.
    return Charsets.UTF_8
        .newDecoder()
        .decode(java.nio.ByteBuffer.wrap(bytes))
        .toString()
}
