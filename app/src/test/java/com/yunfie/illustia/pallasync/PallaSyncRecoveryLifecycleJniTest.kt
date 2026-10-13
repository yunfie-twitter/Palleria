package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.UUID

private const val TEST_THRESHOLD = 2L
private const val TEST_HOLDER_COUNT = 3
private const val TEST_NOW = 10L
private val lifecyclePhrase = (List(23) { "abandon" } + "art").joinToString(" ")

private fun parsed(value: String) = Json.parseToJsonElement(value).jsonObject

private fun recoveryPair(
    manifest: JsonObject,
    ceremony: String,
    firstRole: String,
    secondRole: String,
    purpose: String,
): Pair<PallaSyncRecoveryCandidate, PallaSyncRecoveryCandidate> {
    val session = UUID.randomUUID().toString()
    val admin = manifest.getValue("admin_public_key").jsonPrimitive.content
    val first = PallaSyncCore.beginRecoveryTransfer(manifest.toString(), admin, ceremony, session, "initiator", firstRole, purpose)
    val second = PallaSyncCore.beginRecoveryTransfer(manifest.toString(), admin, ceremony, session, "responder", secondRole, purpose)
    try {
        val sas = PallaSyncCore.bindRecoveryTransfer(first.handle, second.descriptorJson)
        sas shouldBe PallaSyncCore.bindRecoveryTransfer(second.handle, first.descriptorJson)
        // Test endpoints explicitly approve. Product UI must receive two separate human confirmations.
        PallaSyncCore.confirmRecoveryTransfer(first.handle, sas, true)
        PallaSyncCore.confirmRecoveryTransfer(second.handle, sas, true)
        return first to second
    } catch (failure: Throwable) {
        first.close()
        second.close()
        throw failure
    }
}

private fun collectStored(
    collector: Long,
    manifest: JsonObject,
    ceremony: String,
    packageJson: String,
    credential: ByteArray,
    purpose: String,
) {
    val (reader, receiver) = recoveryPair(manifest, ceremony, "reader", "collector", purpose)
    reader.use { sender ->
        receiver.use { recipient ->
            val key = PallaSyncCore.unlockOfflineRecoveryKey(packageJson, credential)
            credential.all { it == 0.toByte() } shouldBe true
            try {
                val packet = PallaSyncCore.releaseRecoveryShare(sender.handle, packageJson, key, true, true, false, false)
                key.all { it == 0.toByte() } shouldBe true
                val accepted = parsed(PallaSyncCore.receiveRecoveryCollector(collector, recipient.handle, packet))
                accepted.getValue("accepted").jsonPrimitive.content shouldBe "true"
                parsed(PallaSyncCore.receiveRecoveryCollector(collector, recipient.handle, packet)) shouldBe accepted
            } finally {
                key.fill(0)
            }
        }
    }
}

class PallaSyncRecoveryLifecycleJniTest :
    StringSpec({
        "JNI distribution readback threshold activation and recovered named root keep all private inputs out of JSON" {
            PallaSyncCore.setSecretForeground(true)
            val instances = List(TEST_HOLDER_COUNT) { UUID.randomUUID().toString() }
            val holders =
                JsonArray(
                    instances.map { instance ->
                        buildJsonObject {
                            put("holder_kind", "offline")
                            put("holder_device_id", JsonNull)
                            put("holder_instance_id", instance)
                        }
                    },
                )
            val packages = mutableListOf<JsonObject>()
            var manifest: JsonObject
            var active: String
            lateinit var originalIdentity: PallaSyncRootSession.Identity
            PallaSyncRootSession.beginText(lifecyclePhrase, "日本語 é  ", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
                originalIdentity = root.identity
                val prepared =
                    parsed(
                        PallaSyncCore.prepareRecoverySet(root.handle, root.operation.wire, 0, TEST_THRESHOLD, holders.toString(), TEST_NOW),
                    )
                manifest = prepared.getValue("manifest").jsonObject
                val statuses = prepared.getValue("status_chain").toString()
                val ceremony = UUID.randomUUID().toString()
                instances.forEachIndexed { index, instance ->
                    val (dealer, issuer) = recoveryPair(manifest, ceremony, "dealer", "reader", "create")
                    dealer.use { source ->
                        issuer.use { target ->
                            val first = PallaSyncCore.distributeRecoveryShare(root.handle, root.operation.wire, source.handle, index + 1L)
                            PallaSyncCore.retryRecoveryTransfer(source.handle, 1) shouldBe first
                            val proposal =
                                PallaSyncCore.receiveRecoveryHolder(
                                    target.handle,
                                    first,
                                    instance,
                                    null,
                                    "passphrase-wrapped",
                                    true,
                                    false,
                                    false,
                                    TEST_NOW,
                                )
                            val signed = PallaSyncCore.receiveRecoveryDealer(root.handle, root.operation.wire, source.handle, proposal)
                            PallaSyncCore.retryRecoveryTransfer(source.handle, 2) shouldBe signed
                            PallaSyncCore.receiveRecoveryDealer(root.handle, root.operation.wire, source.handle, proposal) shouldBe signed
                            parsed(
                                PallaSyncCore.receiveRecoveryHolder(
                                    target.handle,
                                    signed,
                                    instance,
                                    null,
                                    "passphrase-wrapped",
                                    true,
                                    false,
                                    false,
                                    TEST_NOW,
                                ),
                            ).getValue("ready_to_persist")
                                .jsonPrimitive.content shouldBe "true"
                            val credential = strictRootUtf8("independent offline credential $index")
                            val packageJson = PallaSyncCore.recoveryHolderPackage(target.handle, statuses, credential)
                            credential.all { it == 0.toByte() } shouldBe true
                            val stored = parsed(packageJson)
                            stored.keys shouldBe
                                setOf("type", "version", "manifest", "status_chain", "record", "blob", "offline_key_envelope")
                            val readbackCredential = strictRootUtf8("independent offline credential $index")
                            val readbackKey = PallaSyncCore.unlockOfflineRecoveryKey(packageJson, readbackCredential)
                            readbackCredential.all { it == 0.toByte() } shouldBe true
                            try {
                                val ack = PallaSyncCore.recoveryHolderPersisted(target.handle, packageJson, readbackKey)
                                PallaSyncCore.retryRecoveryTransfer(target.handle, 2) shouldBe ack
                                readbackKey.all { it == 0.toByte() } shouldBe true
                                parsed(PallaSyncCore.receiveRecoveryDealer(root.handle, root.operation.wire, source.handle, ack))
                                    .getValue("verified")
                                    .jsonPrimitive.content shouldBe "true"
                                shouldThrow<IllegalStateException> { PallaSyncCore.recoveryHolderWrapKey(target.handle) }
                                packages += stored
                            } finally {
                                readbackKey.fill(0)
                            }
                        }
                    }
                }
                val testCeremony = UUID.randomUUID().toString()
                val collector =
                    PallaSyncCore.beginRecoveryCollection(
                        manifest.toString(),
                        statuses,
                        originalIdentity.adminPublicKey,
                        null,
                        testCeremony,
                        root.operation.wire,
                        root.handle,
                        true,
                        false,
                        false,
                    )
                try {
                    repeat(TEST_THRESHOLD.toInt()) { index ->
                        collectStored(
                            collector,
                            manifest,
                            testCeremony,
                            packages[index].toString(),
                            strictRootUtf8("independent offline credential $index"),
                            "create",
                        )
                    }
                    PallaSyncCore.finishRecoveryCollection(collector, true).use { completion ->
                        completion.thresholdTestPassed shouldBe true
                        completion.identityJson shouldBe null
                    }
                } finally {
                    PallaSyncCore.endRecoveryCollection(collector)
                }
                active = PallaSyncCore.activateRecoverySet(root.handle, root.operation.wire, TEST_NOW + 1)
            }
            // Only encrypted holder packages survive the dealer operation; collector never persists pieces or RMS.
            packages.indices.forEach { index ->
                packages[index] = JsonObject(packages[index] + ("status_chain" to Json.parseToJsonElement(active)))
            }
            val restoreCeremony = UUID.randomUUID().toString()
            val operation = PallaSyncRootSession.Operation.ENROLLMENT
            val collector =
                PallaSyncCore.beginRecoveryCollection(
                    manifest.toString(),
                    active,
                    originalIdentity.adminPublicKey,
                    null,
                    restoreCeremony,
                    operation.wire,
                    0,
                    true,
                    false,
                    false,
                )
            try {
                repeat(TEST_THRESHOLD.toInt()) { index ->
                    collectStored(
                        collector,
                        manifest,
                        restoreCeremony,
                        packages[index].toString(),
                        strictRootUtf8("independent offline credential $index"),
                        "recover",
                    )
                }
                PallaSyncCore.finishRecoveryCollection(collector, true).use { completion ->
                    completion.thresholdTestPassed shouldBe false
                    completion.takeRoot(operation, null) { TEST_NOW }.use { root ->
                        root.identity shouldBe originalIdentity
                        parsed(checkNotNull(completion.identityJson)).keys shouldBe setOf("chain_id", "admin_public_key")
                        PallaSyncCore.cancelRecoverySetSessions(manifest.getValue("recovery_set_id").jsonPrimitive.content)
                        shouldThrow<IllegalStateException> { PallaSyncCore.rootIdentity(root.handle) }.message shouldBe
                            "C_RECOVERY_AUTH_REQUIRED"
                    }
                }
            } finally {
                PallaSyncCore.endRecoveryCollection(collector)
            }
        }

        "malformed public package still erases supplied RSWK and consumes its pair" {
            PallaSyncCore.setSecretForeground(true)
            PallaSyncRootSession.beginText(lifecyclePhrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
                val holders =
                    JsonArray(
                        List(TEST_HOLDER_COUNT) {
                            buildJsonObject {
                                put("holder_kind", "offline")
                                put("holder_device_id", JsonNull)
                                put("holder_instance_id", UUID.randomUUID().toString())
                            }
                        },
                    )
                val prepared =
                    parsed(
                        PallaSyncCore.prepareRecoverySet(root.handle, root.operation.wire, 0, TEST_THRESHOLD, holders.toString(), TEST_NOW),
                    )
                val manifest = prepared.getValue("manifest").jsonObject
                val (reader, collector) = recoveryPair(manifest, UUID.randomUUID().toString(), "reader", "collector", "recover")
                reader.use {
                    collector.use {
                        val key = ByteArray(32) { 1 }
                        shouldThrow<IllegalStateException> {
                            PallaSyncCore.releaseRecoveryShare(
                                reader.handle,
                                "{}",
                                key,
                                true,
                                true,
                                false,
                                false,
                            )
                        }
                        key.all { it == 0.toByte() } shouldBe true
                        shouldThrow<IllegalStateException> {
                            PallaSyncCore.bindRecoveryTransfer(reader.handle, collector.descriptorJson)
                        }.message shouldBe "C_RECOVERY_SESSION_EXPIRED"
                    }
                }
            }
        }
    })
