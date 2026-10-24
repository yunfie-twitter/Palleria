package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

class PallaSyncRecoveryMediaJniTest :
    StringSpec({
        val packageJson =
            checkNotNull(PallaSyncRecoveryMediaJniTest::class.java.getResourceAsStream("/pallasync/python-recovery-c8-package.json"))
                .use { it.readBytes().toString(Charsets.UTF_8).trim() }
        val source = Json.parseToJsonElement(packageJson).jsonObject
        val admin =
            source
                .getValue("manifest")
                .jsonObject
                .getValue("admin_public_key")
                .jsonPrimitive.content
        val head =
            checkNotNull(
                PallaSyncCore.objectHash(
                    source
                        .getValue("status_chain")
                        .jsonArray
                        .last()
                        .toString(),
                ),
            )
        "public Python C8 package uses the exact PSR3 encoding and rejects a forged identity" {
            val canonical = PallaSyncCore.validateRecoveryPackage(packageJson, admin, head)
            val encoded = PallaSyncCore.encodeRecoveryPortable(packageJson, admin, head)
            encoded.toString(Charsets.US_ASCII) shouldBe
                "PSR3:" + Base64.getUrlEncoder().withoutPadding().encodeToString(canonical.toByteArray())
            PallaSyncCore.decodeRecoveryPortable(encoded, admin, head) shouldBe canonical
            shouldThrow<IllegalStateException> {
                PallaSyncCore.decodeRecoveryPortable(encoded, Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32)), head)
            }.message shouldBe "C_RECOVERY_SHARE_INVALID"
            shouldThrow<IllegalStateException> {
                PallaSyncCore.decodeRecoveryPortable(encoded + byteArrayOf('='.code.toByte()), admin, head)
            }.message shouldBe "C_RECOVERY_SHARE_INVALID"
            shouldThrow<IllegalStateException> {
                PallaSyncCore.decodeRecoveryPortable(ByteArray(5_592_414), admin, head)
            }.message shouldBe "C_RECOVERY_SHARE_INVALID"
        }
        "fragment assembly validates the complete signed package and retains no incomplete authority" {
            val fragments = PallaSyncCore.recoveryPackageFragments(packageJson, admin, head, 1000)
            PallaSyncCore.assembleRecoveryFragments(fragments.take(1).toTypedArray(), admin, head) shouldBe null
            val canonical = PallaSyncCore.validateRecoveryPackage(packageJson, admin, head)
            PallaSyncCore.assembleRecoveryFragments(fragments.reversedArray() + fragments.first(), admin, head) shouldBe canonical
            val first = Json.parseToJsonElement(fragments.first()).jsonObject
            val conflicting = JsonObject(first + ("data" to JsonPrimitive(first.getValue("data").jsonPrimitive.content + "x")))
            shouldThrow<IllegalStateException> {
                PallaSyncCore.assembleRecoveryFragments(arrayOf(fragments.first(), conflicting.toString()), admin, head)
            }.message shouldBe "C_RECOVERY_SHARE_INVALID"
            val wrong = JsonObject(first + ("part_count" to JsonPrimitive(4097)))
            shouldThrow<IllegalStateException> {
                PallaSyncCore.assembleRecoveryFragments(arrayOf(wrong.toString()), admin, head)
            }.message shouldBe "C_RECOVERY_SHARE_INVALID"
            val statuses = source.getValue("status_chain").jsonArray
            val tampered = JsonObject(statuses.first().jsonObject + ("created_at_ms" to JsonPrimitive(2)))
            val forged = JsonObject(source + ("status_chain" to JsonArray(listOf(tampered)))).toString()
            shouldThrow<IllegalStateException> {
                PallaSyncCore.encodeRecoveryPortable(forged, admin, null)
            }.message shouldBe "C_RECOVERY_SHARE_INVALID"
        }
    })
