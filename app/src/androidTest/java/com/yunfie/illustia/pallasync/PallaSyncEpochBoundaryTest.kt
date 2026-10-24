package com.yunfie.illustia.pallasync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android JNI opens only normal epoch secrets from independent public relay artifacts. */
@RunWith(AndroidJUnit4::class)
class PallaSyncEpochBoundaryTest {
    @Test
    fun completeAuthorityPrecedesRecoveryCommitmentAndEpochSpecificDecryption() {
        val fixture =
            InstrumentationRegistry.getInstrumentation().context.assets.open("pallasync/python-epoch-acceptance.json").use {
                Json.parseToJsonElement(it.reader(Charsets.UTF_8).readText()).jsonObject
            }
        val entries =
            listOf(
                fixture.getValue("log_pages"),
                fixture.getValue("enrollment").jsonObject.getValue("log_pages"),
                fixture.getValue("epoch_checks").jsonObject.getValue("log_pages"),
            ).flatMap { pages ->
                pages.jsonArray.flatMap { it.jsonObject.getValue("items").jsonArray }
            }.map { it.jsonObject }
        val authority = authority(fixture, entries)
        val identity = fixture.getValue("identity").jsonObject
        val phrase = (List(23) { "abandon" } + "art").joinToString(" ").toByteArray()
        PallaSyncRootSession
            .begin(
                phrase,
                byteArrayOf(),
                PallaSyncRootSession.Operation.ENROLLMENT,
                identity.getValue("chain_id").jsonPrimitive.content,
                identity.getValue("admin_public_key").jsonPrimitive.content,
            ).use { root ->
                assertTrue(phrase.all { it == 0.toByte() })
                for (epoch in 0L..2L) {
                    val signed =
                        authority
                            .getValue("epochs")
                            .jsonObject
                            .getValue(epoch.toString())
                            .jsonObject
                            .getValue("record")
                            .jsonObject
                    val envelope =
                        entries
                            .single {
                                it.getValue("kind").jsonPrimitive.content == "envelope" &&
                                    it.getValue("object").jsonObject.let { obj ->
                                        obj.getValue("recipient_kind").jsonPrimitive.content == "recovery" &&
                                            obj.getValue("epoch").jsonPrimitive.long == epoch
                                    }
                            }.getValue("object")
                    val secret =
                        PallaSyncCore.recoverEpochSecret(
                            root.handle,
                            root.operation.wire,
                            authority.toString(),
                            buildJsonObject {
                                put("envelope", envelope)
                            }.toString().toByteArray(),
                            epoch,
                        )
                    val context =
                        buildJsonObject {
                            for (field in listOf("chain_id", "generation", "epoch", "epoch_commitment")) put(field, signed.getValue(field))
                            put("metadata_profile", "private")
                        }
                    PallaSyncEpochSession.begin(context, secret).use { session ->
                        assertTrue(secret.all { it == 0.toByte() })
                        assertEquals(null, session.tag(SEARCH_HISTORY_SCHEMA_V3))
                        for (entry in entries.filter {
                            it.getValue("kind").jsonPrimitive.content == "record" &&
                                it
                                    .getValue("object")
                                    .jsonObject
                                    .getValue("epoch")
                                    .jsonPrimitive.long == epoch
                        }) {
                            val objectJson = entry.getValue("object").toString()
                            assertTrue(
                                PallaSyncCore.validateRecordAuthority(
                                    authority.toString(),
                                    objectJson,
                                    entry.getValue("relay_seq").jsonPrimitive.long,
                                ),
                            )
                            val inner = Json.parseToJsonElement(session.decryptRecord(objectJson)).jsonObject
                            assertEquals(SEARCH_HISTORY_SCHEMA_V3, inner.getValue("collection").jsonPrimitive.content)
                            val query =
                                inner
                                    .getValue(
                                        "operations",
                                    ).jsonArray
                                    .single()
                                    .jsonObject
                                    .getValue("body")
                                    .jsonObject
                                    .getValue("query")
                                    .jsonPrimitive.content
                            assertEquals(if (epoch == 0L) "猫" else "epoch-one", query)
                        }
                    }
                }
            }
    }

    private fun authority(
        fixture: JsonObject,
        entries: List<JsonObject>,
    ): JsonObject {
        val enrollment = fixture.getValue("enrollment").jsonObject
        val epochs = fixture.getValue("epoch_checks").jsonObject
        val responses =
            listOf(
                fixture.getValue("created"),
                fixture.getValue("record_upload"),
                enrollment.getValue("accepted"),
                epochs.getValue("manual_accepted"),
                epochs.getValue("record_upload"),
                epochs.getValue("revocation_accepted"),
            )
        val receipts =
            responses
                .flatMap {
                    it.jsonObject.getValue("upload_receipts").jsonArray
                }.associateBy {
                    it.jsonObject
                        .getValue("relay_seq")
                        .jsonPrimitive.long
                }
        var state =
            PallaSyncCore.beginAuthorityHistory(
                fixture.getValue("parameters").toString().toByteArray(),
                fixture.getValue("identity").toString(),
                fixture.getValue("origin").jsonPrimitive.content,
                fixture.getValue("relay_public_key").jsonPrimitive.content,
            )
        val revoked = epochs.getValue("revocation_body").jsonObject
        val payload =
            buildJsonObject {
                put("epoch", revoked.getValue("epoch"))
                put("envelopes", revoked.getValue("envelopes"))
            }
        state = PallaSyncCore.bindAuthorityActionPayload(state, revoked.getValue("admin_operation").toString(), payload.toString())
        for (entry in entries) {
            state =
                PallaSyncCore.advanceAuthorityHistory(
                    state,
                    entry.toString(),
                    receipts.getValue(entry.getValue("relay_seq").jsonPrimitive.long).toString(),
                )
        }
        PallaSyncCore.validateInitialHistoryCut(
            state,
            buildJsonObject {
                put("head", epochs.getValue("transparency_head"))
            }.toString().toByteArray(),
        )
        return Json.parseToJsonElement(state).jsonObject
    }
}
