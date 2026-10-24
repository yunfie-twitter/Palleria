package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Transparency entry_index is kept separate from relay_seq/data-log positions (§17.8/§17.10). */
internal data class PallaSyncTransparencyProgress(
    val verifiedIndex: Long,
    val observedTargetIndex: Long,
    val observationConflict: Boolean,
) {
    companion object {
        fun shouldResumeSavedTarget(
            savedTargetIndex: Long,
            verifiedIndex: Long,
        ): Boolean = savedTargetIndex > verifiedIndex

        fun fromSavedHead(
            transparencyId: String?,
            logId: String?,
            verifiedIndex: Long,
            savedHeadJson: String?,
        ): PallaSyncTransparencyProgress {
            val observation = savedHeadJson?.let(::parseHead)
            return when {
                savedHeadJson == null -> PallaSyncTransparencyProgress(verifiedIndex, verifiedIndex, false)
                observation == null -> PallaSyncTransparencyProgress(verifiedIndex, verifiedIndex, true)
                else -> observation.toProgress(transparencyId, logId, verifiedIndex)
            }
        }

        private fun parseHead(value: String): HeadObservation? =
            runCatching {
                val head = Json.parseToJsonElement(value).jsonObject
                HeadObservation(
                    head.getValue("transparency_id").jsonPrimitive.content,
                    head.getValue("log_id").jsonPrimitive.content,
                    head.getValue("entry_index").jsonPrimitive.long,
                )
            }.getOrNull()

        private data class HeadObservation(
            val transparencyId: String,
            val logId: String,
            val entryIndex: Long,
        ) {
            fun toProgress(
                expectedTransparencyId: String?,
                expectedLogId: String?,
                verifiedIndex: Long,
            ): PallaSyncTransparencyProgress {
                val expectedIdentity = expectedTransparencyId to expectedLogId
                val observedIdentity = transparencyId to logId
                val identityMatches =
                    expectedTransparencyId != null && expectedLogId != null && expectedIdentity == observedIdentity
                val validIndex = entryIndex >= 0
                return if (!identityMatches || !validIndex) {
                    PallaSyncTransparencyProgress(verifiedIndex, verifiedIndex, true)
                } else {
                    PallaSyncTransparencyProgress(verifiedIndex, maxOf(verifiedIndex, entryIndex), false)
                }
            }
        }
    }
}
