package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Independent Python/relay artifacts cross JNI; no private keys appear in the fixture. */
class PallaSyncEpochHistoryJniTest :
    StringSpec({
        "exact admin action binding precedes publication of a rotated and revoked epoch" {
            val f =
                Json
                    .parseToJsonElement(
                        PallaSyncEpochHistoryJniTest::class.java
                            .getResourceAsStream(
                                "/pallasync/python-epoch-acceptance.json",
                            )!!
                            .bufferedReader()
                            .use {
                                it.readText()
                            },
                    ).jsonObject
            val epochs = f.getValue("epoch_checks").jsonObject
            val enrol = f.getValue("enrollment").jsonObject
            var authority =
                PallaSyncCore.beginAuthorityHistory(
                    f.getValue("parameters").toString().toByteArray(),
                    f.getValue("identity").toString(),
                    f.getValue("origin").jsonPrimitive.content,
                    f.getValue("relay_public_key").jsonPrimitive.content,
                )
            val revoked = epochs.getValue("revocation_body").jsonObject
            val action =
                buildJsonObject {
                    put("epoch", revoked.getValue("epoch"))
                    put("envelopes", revoked.getValue("envelopes"))
                }
            var bound =
                PallaSyncCore.bindAuthorityActionPayload(
                    authority,
                    revoked.getValue("admin_operation").toString(),
                    action.toString(),
                )
            val receipts =
                listOf(
                    f.getValue("created"),
                    f.getValue("record_upload"),
                    enrol.getValue("accepted"),
                    epochs.getValue("manual_accepted"),
                    epochs.getValue("record_upload"),
                    epochs.getValue("revocation_accepted"),
                ).flatMap {
                    it.jsonObject.getValue("upload_receipts").jsonArray
                }.associateBy {
                    it.jsonObject
                        .getValue("relay_seq")
                        .jsonPrimitive.long
                }
            val pages = f.getValue("log_pages").jsonArray + enrol.getValue("log_pages").jsonArray + epochs.getValue("log_pages").jsonArray
            for (page in pages) {
                for (entry in page.jsonObject.getValue("items").jsonArray) {
                    val seq =
                        entry.jsonObject
                            .getValue("relay_seq")
                            .jsonPrimitive.long
                    val receipt = receipts.getValue(seq).toString()
                    bound = PallaSyncCore.advanceAuthorityHistory(bound, entry.toString(), receipt)
                    if (seq == 18L) {
                        shouldThrow<IllegalStateException> {
                            PallaSyncCore.advanceAuthorityHistory(
                                authority,
                                entry.toString(),
                                receipt,
                            )
                        }.message shouldBe
                            "C_MISSING_DEPENDENCY"
                    } else {
                        authority = PallaSyncCore.advanceAuthorityHistory(authority, entry.toString(), receipt)
                    }
                }
            }
            Json
                .parseToJsonElement(bound)
                .jsonObject
                .getValue("sequence")
                .jsonPrimitive.long shouldBe 18L
            PallaSyncCore.validateInitialHistoryCut(
                bound,
                buildJsonObject {
                    put("head", epochs.getValue("transparency_head"))
                }.toString().toByteArray(),
            )
        }
    })
