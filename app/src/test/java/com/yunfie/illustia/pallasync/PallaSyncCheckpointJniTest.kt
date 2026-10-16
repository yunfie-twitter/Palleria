package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import java.util.UUID

class PallaSyncCheckpointJniTest {
    @Test
    fun `certified device session signs and native verifier rejects changed or untrusted checkpoints`() {
        val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
        PallaSyncCore.setSecretForeground(true)
        val root = PallaSyncCore.beginRootSession(phrase.toByteArray(), byteArrayOf(), "genesis", null, null)
        try {
            PallaSyncCore.createGenesisBundle(root, "checkpoint JNI", "os-keystore", "https://relay.example.com").use { prepared ->
                val keys = prepared.snapshot()
                val genesis =
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(prepared.publicResult.requestBody)
                        .jsonObject
                val parameters = genesis.getValue("parameters")
                val epoch = genesis.getValue("epoch")
                val hash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                val template =
                    buildJsonObject {
                        put("type", "PALLASYNC-CHECKPOINT-v3")
                        put("version", "3.0")
                        put("checkpoint_id", UUID.randomUUID().toString())
                        put("chain_id", checkNotNull(keys.chainId))
                        put("generation", 0)
                        put("device_id", checkNotNull(keys.deviceId))
                        put("relay_origin", "https://relay.example.com")
                        put("relay_key_id", hash)
                        put("log_id", UUID.randomUUID().toString())
                        put("seq", 0)
                        put("head_hash", hash)
                        put("parameters_hash", checkNotNull(PallaSyncCore.objectHash(parameters.toString())))
                        put("epoch_hash", checkNotNull(PallaSyncCore.objectHash(epoch.toString())))
                        put("transparency_id", UUID.randomUUID().toString())
                        put("transparency_index", 0)
                        put("transparency_hash", hash)
                        put("created_at_ms", 1_791_684_000_000L)
                        put("signature", "A".repeat(86))
                    }
                val canonicalTemplate = checkNotNull(PallaSyncCore.canonicalJson(template.toString()))
                val signed =
                    PallaSyncDeviceSession.begin(keys).use { session ->
                        session.signCheckpoint(canonicalTemplate, checkNotNull(keys.deviceId), keys.publicKeyBase64Url)
                    }
                val canonical = checkNotNull(PallaSyncCore.canonicalJson(signed))
                canonical shouldBe signed
                PallaSyncCore.verifyDeviceCheckpoint(signed, keys.publicKeyBase64Url) shouldBe true
                PallaSyncCore.verifyDeviceCheckpoint(signed, hash) shouldBe false
                val changed = signed.replace("\"seq\":0", "\"seq\":1")
                PallaSyncCore.verifyDeviceCheckpoint(changed, keys.publicKeyBase64Url) shouldBe false
                val unknown = signed.dropLast(1) + ",\"unknown\":true}"
                PallaSyncCore.verifyDeviceCheckpoint(unknown, keys.publicKeyBase64Url) shouldBe false
            }
        } finally {
            PallaSyncCore.endRootSession(root)
        }
    }
}
