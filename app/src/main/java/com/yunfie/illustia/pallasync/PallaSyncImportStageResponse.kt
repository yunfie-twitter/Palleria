package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

/** §17.5: accept only the exact, non-writable staging acknowledgement for the selected chain. */
internal object PallaSyncImportStageResponse {
    private const val MAX_RESPONSE_BYTES = 4_096

    data class Staged(
        val chainId: String,
        val logId: String,
    )

    fun validate(
        bytes: ByteArray,
        expectedChainId: String,
    ): Staged {
        check(bytes.size in 1..MAX_RESPONSE_BYTES) { "C_SCHEMA_VIOLATION" }
        val text =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        check(PallaSyncCore.canonicalJson(text) == text) { "C_SCHEMA_VIOLATION" }
        val response = Json.parseToJsonElement(text).jsonObject
        check(response.keys == setOf("chain_id", "state", "log_id")) { "C_SCHEMA_VIOLATION" }
        val chainId = response.getValue("chain_id").jsonPrimitive.content
        val state = response.getValue("state").jsonPrimitive.content
        val logId = response.getValue("log_id").jsonPrimitive.content
        check(chainId == expectedChainId && state == "staged" && isUuid4(logId)) { "C_SCHEMA_VIOLATION" }
        return Staged(chainId, logId)
    }

    private fun isUuid4(value: String): Boolean =
        runCatching { UUID.fromString(value) }.getOrNull()?.let {
            it.version() == 4 && it.variant() == 2 && it.toString() == value
        } == true
}
