package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** §13.5: peer approvals receive only same-generation epochs still distributable under the signed policy. */
class PallaSyncPeerEpochEligibilityTest :
    StringSpec({
        "retain distributes the complete available generation history" {
            PallaSyncPeerEpochEligibility.eligibleEpochs(authority("retain"), 50) shouldBe listOf(0L, 1L, 2L)
        }

        "windowed expires an old key exactly at successor commit plus the configured window" {
            val authority = authority("windowed", windowMs = 20)
            PallaSyncPeerEpochEligibility.eligibleEpochs(authority, 119) shouldBe listOf(0L, 1L, 2L)
            PallaSyncPeerEpochEligibility.eligibleEpochs(authority, 120, handoffVerified = true) shouldBe listOf(1L, 2L)
            PallaSyncPeerEpochEligibility.eligibleEpochs(authority, 220, handoffVerified = true) shouldBe listOf(2L)
        }

        "windowed expiry is postponed and immediate enrollment is blocked until handoff is verified" {
            val windowed = authority("windowed", windowMs = 20)
            PallaSyncPeerEpochEligibility.eligibleEpochs(windowed, 220) shouldBe listOf(0L, 1L, 2L)
            shouldThrow<IllegalStateException> {
                PallaSyncPeerEpochEligibility.eligibleEpochs(authority("immediate"), 50)
            }.message shouldBe "C_MISSING_DEPENDENCY"
            PallaSyncPeerEpochEligibility.eligibleEpochs(authority("immediate"), 50, handoffVerified = true) shouldBe listOf(2L)
            PallaSyncPeerEpochEligibility.eligibleEpochs(authority("immediate", numbers = listOf(0)), 50) shouldBe listOf(0L)
        }

        "epoch gaps are rejected" {
            shouldThrow<IllegalStateException> {
                PallaSyncPeerEpochEligibility.eligibleEpochs(authority("retain", numbers = listOf(0, 2)), 50)
            }.message shouldBe "C_MISSING_DEPENDENCY"
        }
    })

private fun authority(
    retention: String,
    windowMs: Long = 20,
    numbers: List<Int> = listOf(0, 1, 2),
): JsonObject =
    Json
        .parseToJsonElement(
            buildString {
                append("{\"parameters\":{\"generation\":0,\"policy\":{\"epoch_retention\":\"")
                append(retention)
                append("\",\"epoch_window_ms\":")
                append(windowMs)
                append("}},\"epochs\":{")
                numbers.forEachIndexed { index, epoch ->
                    if (index > 0) append(',')
                    append('"').append(epoch).append("\":{\"record\":{\"epoch\":")
                    append(epoch)
                    append(",\"generation\":0},\"committed_at_ms\":")
                    append(epoch * 100)
                    append('}')
                }
                append("}}")
            },
        ).jsonObject
