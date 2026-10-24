package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncEnvelopeRepairJniTest {
    @Test
    fun `root repair returns only a signed device envelope and scopes upload capability`() {
        PallaSyncCore.setSecretForeground(true)
        val genesis =
            PallaSyncRootSession.beginText(MNEMONIC, "", PallaSyncRootSession.Operation.GENESIS).use { root ->
                PallaSyncCore.createGenesisBundle(root.handle, "repair-test", aud = ORIGIN)
            }
        genesis.use {
            val genesisBody = Json.parseToJsonElement(it.publicResult.requestBody).jsonObject
            val recovery =
                genesisBody
                    .getValue("envelopes")
                    .jsonArray
                    .map { envelope -> envelope.jsonObject }
                    .single { envelope -> envelope.getValue("recipient_kind").jsonPrimitive.content == "recovery" }
            PallaSyncRootSession
                .beginText(
                    MNEMONIC,
                    "",
                    PallaSyncRootSession.Operation.ENVELOPE_REPAIR,
                    it.publicResult.chainId,
                    PallaSyncRootSession.beginText(MNEMONIC, "", PallaSyncRootSession.Operation.GENESIS).use { root ->
                        root.identity.adminPublicKey
                    },
                    requestGuard = {},
                    clock = { NOW },
                ).use { repair ->
                    val envelopeJson =
                        Json.parseToJsonElement(
                            repair.createReplacementDeviceEnvelope(
                                recovery.toString(),
                                it.publicResult.deviceId,
                                it.publicResult.deviceKexPublicKey,
                            ),
                        )
                    val envelope = envelopeJson.jsonObject
                    envelope.getValue("chain_id").jsonPrimitive.content shouldBe it.publicResult.chainId
                    envelope.getValue("recipient_device_id").jsonPrimitive.content shouldBe it.publicResult.deviceId
                    envelope.getValue("signer_kind").jsonPrimitive.content shouldBe "admin"
                    envelope.getValue("recipient_kind").jsonPrimitive.content shouldBe "device"
                    envelope.keys.contains("epoch_secret") shouldBe false
                    checkNotNull(
                        repair.token(
                            ORIGIN,
                            "POST",
                            "/pallasync/v3/chains/${it.publicResult.chainId}/epochs/0/envelopes",
                            body = "{}".toByteArray(),
                        ),
                    )
                    val denied =
                        runCatching {
                            repair.token(ORIGIN, "POST", "/pallasync/v3/chains/${it.publicResult.chainId}/epochs")
                        }.exceptionOrNull()
                    denied?.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                }
        }
    }

    private companion object {
        const val ORIGIN = "https://relay.example.com"
        const val NOW = 1_791_684_000_000L
        val MNEMONIC = formatMnemonic()

        fun formatMnemonic() = "${List(23) { "abandon" }.joinToString(" ")} art"
    }
}
