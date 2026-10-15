package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class PallaSyncDeletionEvidenceMergeTest {
    @Test
    fun `parallel archives merge distinct witnesses in stable device order`() {
        val first = archive("11111111-1111-4111-8111-111111111111", "witness-a")
        val second = archive("22222222-2222-4222-8222-222222222222", "witness-b")

        val merged = checkNotNull(PallaSyncDeletionEvidenceMerge.merge(listOf(first, second)))
        val witnesses =
            merged
                .getValue("evidence_bundle")
                .jsonObject
                .getValue("witnesses")
                .jsonArray

        witnesses.map {
            it.jsonObject
                .getValue("witness_device_id")
                .jsonPrimitive.content
        } shouldBe
            listOf("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222")
        merged.getValue("delete_receipt") shouldBe first.getValue("delete_receipt")
    }

    @Test
    fun `divergent signature for one witness identity fails as equivocation`() {
        val first = archive("11111111-1111-4111-8111-111111111111", "witness-a")
        val conflicting = archive("11111111-1111-4111-8111-111111111111", "different-signature")

        shouldThrow<IllegalStateException> {
            PallaSyncDeletionEvidenceMerge.merge(listOf(first, conflicting))
        }.message shouldBe "C_DEVICE_EQUIVOCATION"
    }

    @Test
    fun `archives with a different receipt binding cannot be merged`() {
        val first = archive("11111111-1111-4111-8111-111111111111", "witness-a")
        val conflicting =
            Json
                .parseToJsonElement(
                    """{"delete_receipt":{"deletion_id":"deletion"},"evidence_bundle":{"type":"PALLASYNC-DELETION-EVIDENCE-v3","version":"3.0","chain_id":"chain","deletion_id":"deletion","delete_receipt_hash":"other-receipt-hash","witnesses":[{"witness_device_id":"22222222-2222-4222-8222-222222222222","signature":"witness-b"}]}}""",
                ).jsonObject

        shouldThrow<IllegalStateException> {
            PallaSyncDeletionEvidenceMerge.merge(listOf(first, conflicting))
        }.message shouldBe "C_DELETION_UNVERIFIED"
    }

    @Test
    fun `merged bundle cannot exceed the protocol witness limit`() {
        val archives =
            (0..128).map { index ->
                archive("00000000-0000-4000-8000-${index.toString().padStart(12, '0')}", "witness-$index")
            }

        shouldThrow<IllegalStateException> {
            PallaSyncDeletionEvidenceMerge.merge(archives)
        }.message shouldBe "C_SCHEMA_VIOLATION"
    }

    private fun archive(
        device: String,
        signature: String,
    ) = Json
        .parseToJsonElement(
            """{"delete_receipt":{"deletion_id":"deletion"},"evidence_bundle":{"type":"PALLASYNC-DELETION-EVIDENCE-v3","version":"3.0","chain_id":"chain","deletion_id":"deletion","delete_receipt_hash":"receipt-hash","witnesses":[{"witness_device_id":"$device","signature":"$signature"}]}}""",
        ).jsonObject
}
