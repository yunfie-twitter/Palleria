package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

private const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991L

internal fun pallaSyncAuthorityGeneration(authority: JsonObject): Long =
    authority
        .getValue("parameters")
        .jsonObject
        .getValue("generation")
        .jsonPrimitive.long

/** §13.5/§18.1: old keys are shared only while the signed retention policy keeps them eligible. */
internal object PallaSyncPeerEpochEligibility {
    fun eligibleEpochs(
        authority: JsonObject,
        nowMs: Long,
        handoffVerified: Boolean = false,
    ): List<Long> {
        check(nowMs >= 0) { "C_SCHEMA_VIOLATION" }
        val generation = pallaSyncAuthorityGeneration(authority)
        val records =
            authority
                .getValue("epochs")
                .jsonObject
                .values
                .map { item ->
                    val value = item.jsonObject
                    val record = value.getValue("record").jsonObject
                    Epoch(
                        number = record.getValue("epoch").jsonPrimitive.long,
                        generation = record.getValue("generation").jsonPrimitive.long,
                        committedAtMs = value.getValue("committed_at_ms").jsonPrimitive.long,
                    )
                }.filter { it.generation == generation }
                .sortedBy(Epoch::number)
        check(records.isNotEmpty() && records.zipWithNext().all { (left, right) -> right.number == left.number + 1 }) {
            "C_MISSING_DEPENDENCY"
        }
        val policy =
            authority
                .getValue("parameters")
                .jsonObject
                .getValue("policy")
                .jsonObject
        val current = records.last().number
        return when (policy.getValue("epoch_retention").jsonPrimitive.content) {
            "retain" -> {
                records.map(Epoch::number)
            }

            "immediate" -> {
                check(handoffVerified || records.size == 1) { "C_MISSING_DEPENDENCY" }
                listOf(current)
            }

            "windowed" -> {
                val window = policy.getValue("epoch_window_ms").jsonPrimitive.long
                check(window >= 0) { "C_SCHEMA_VIOLATION" }
                records.mapIndexedNotNull { index, epoch ->
                    val successor = records.getOrNull(index + 1)
                    if (successor == null) {
                        epoch.number
                    } else {
                        check(successor.committedAtMs >= 0 && successor.committedAtMs <= MAX_SAFE_JSON_INTEGER - window) {
                            "C_SCHEMA_VIOLATION"
                        }
                        val deadline = successor.committedAtMs + window
                        epoch.number.takeIf { nowMs < deadline || !handoffVerified }
                    }
                }
            }

            else -> {
                error("C_SCHEMA_VIOLATION")
            }
        }
    }

    private data class Epoch(
        val number: Long,
        val generation: Long,
        val committedAtMs: Long,
    )
}
