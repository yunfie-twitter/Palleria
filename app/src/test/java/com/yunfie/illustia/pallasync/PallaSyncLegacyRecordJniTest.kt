package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.util.Base64

class PallaSyncLegacyRecordJniTest {
    @Test
    fun `v21 device registry verifier rejects incomplete rows`() {
        PallaSyncCore.verifyLegacyDeviceRecordV21("{}") shouldBe false
    }

    @Test
    fun `v21 migration token only signs authenticated read paths and binds pagination`() {
        val seed = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 41 })
        val chainId = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val deviceId = "01890f3e-7c00-7000-8000-000000000002"
        val path = "/pallasync/v2/chains/$chainId/records"
        val query = "after_seq=0&limit=200"

        val encoded = PallaSyncCore.legacyV21GetToken(seed, chainId, deviceId, path, query, 1_700_000_000_000)
        val tokenBytes = Base64.getUrlDecoder().decode(encoded)
        val token = Json.parseToJsonElement(tokenBytes.toString(Charsets.UTF_8)).jsonObject
        token.getValue("method").jsonPrimitive.content shouldBe "GET"
        token.getValue("query").jsonPrimitive.content shouldBe query
        token.getValue("expires_at_ms").jsonPrimitive.content shouldBe "1700000300000"

        shouldThrow<IllegalStateException> {
            PallaSyncCore.legacyV21GetToken(seed, chainId, deviceId, "/pallasync/v2/chains/$chainId/delete", "", 1_700_000_000_000)
        }
        shouldThrow<IllegalStateException> {
            PallaSyncCore.legacyV21GetToken(seed, chainId, deviceId, path, "limit=200", 1_700_000_000_000)
        }
    }

    @Test
    fun `v20 legacy root session validates deterministic chain id and ends natively`() {
        val mnemonic =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val chainId = "_iefvXsLUE_of7LKvJB_z-e6-OXGP5oG6SClqLIA8Ds"

        val handle = PallaSyncCore.beginLegacyV20MigrationSession(mnemonic, "", chainId)
        (handle > 0) shouldBe true
        PallaSyncCore.endLegacyMigrationSession(handle)

        shouldThrow<IllegalStateException> {
            PallaSyncCore.beginLegacyV20MigrationSession(
                mnemonic,
                "",
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            )
        }

        shouldThrow<IllegalStateException> {
            PallaSyncCore.beginLegacyV20MigrationSession(mnemonic, "wrong", chainId)
        }
    }

    @Test
    fun `v21 root inputs validate only the supplied legacy public identity`() {
        val mnemonic =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art"
        val salt = "BAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ"
        val chainId = "g76yyPCKGrNpNrfPgHGqdNsMmTLCZMg1lawtIPENYcM"
        val adminPublicKey = "sidFYvDxRt_JRoVJDO4FSBEqpI6Ty4e08S0wKUGBu0I"

        fun matches(
            passphrase: String,
            candidateChainId: String = chainId,
            candidateSalt: String = salt,
        ) = PallaSyncCore.validateLegacyV21Identity(mnemonic, passphrase, candidateChainId, candidateSalt, adminPublicKey)

        matches("") shouldBe true
        matches("wrong passphrase") shouldBe false
        matches("", candidateChainId = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA") shouldBe false
        matches("", candidateSalt = "not-a-salt") shouldBe false

        val handle = PallaSyncCore.beginLegacyMigrationSession(mnemonic, "", salt, chainId, adminPublicKey)
        (handle > 0) shouldBe true
        PallaSyncCore.endLegacyMigrationSession(handle)
    }

    @Test
    fun `v21 record signature uses legacy context and omits relay assigned sequences`() {
        val publicKey = "Zs1gi5KLiOUODv6qM_rxxDzv4HKUsLh-n-CrpqPPdjM"
        val record =
            """{"action":"upsert","chain_id":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","collection_name":"palleria.favorite_tag/2","created_at_ms":1700000000000,"device_id":"01890f3e-7c00-7000-8000-000000000002","encrypted_payload":"BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc","epoch":0,"lamport":1,"payload_nonce":"CAgICAgICAgICAgICAgICAgICAgICAgI","protocol_version":"2.1","record_id":"01890f3e-7c00-7000-8000-000000000001","signature":"b_ipAK6qA2e26iF8FVDBA5KzlsKXmD8tboi3d9DgDov9ed_h5Bnj-YNsl7z-HqsDEEc0fwqLrCVWJTeQYvsiBA"}"""

        PallaSyncCore.verifyLegacySyncRecordV21(record, publicKey) shouldBe true
        PallaSyncCore.verifyLegacySyncRecordV21(record.dropLast(1) + ",\"relay_seq\":19}", publicKey) shouldBe true
        PallaSyncCore.verifyLegacySyncRecordV21(record.replace("\"lamport\":1", "\"lamport\":2"), publicKey) shouldBe false
        PallaSyncCore.verifyLegacySyncRecordV21(record.dropLast(1) + ",\"unknown\":true}", publicKey) shouldBe false
    }
}
