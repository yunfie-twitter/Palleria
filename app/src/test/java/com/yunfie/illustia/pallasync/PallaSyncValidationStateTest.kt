package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncValidationStateTest {
    @Test
    fun `missing dependency keeps page cursor uncommitted`() {
        recordPageMustRemainUncommitted("C_MISSING_DEPENDENCY") shouldBe true
        recordPageMustRemainUncommitted("C_SCHEMA_VIOLATION") shouldBe false
        recordPageMustRemainUncommitted(null) shouldBe false
    }

    @Test
    fun `local operational prerequisites never turn signed log objects into corrupt quarantine`() {
        listOf(
            "C_LOCAL_LIMIT",
            "C_LOCAL_STORAGE_FULL",
            "C_WRITE_PAUSED_BY_POLICY",
            "C_COUNTER_EXHAUSTED",
            "C_ORDER_KEY_EXHAUSTED",
            "C_RECOVERY_PARAMETER_INVALID",
            "C_RECOVERY_THRESHOLD_NOT_MET",
            "C_RECOVERY_SET_MISMATCH",
            "C_RECOVERY_SHARE_ALREADY_USED",
            "C_RECOVERY_WRAP_KEY_UNAVAILABLE",
            "C_RECOVERY_AUTH_REQUIRED",
            "C_RECOVERY_STORAGE_UNSAFE",
            "C_RELAY_DISCLOSURE_INVALID",
            "C_RELAY_POLICY_UNAVAILABLE",
            "C_RELAY_POLICY_CONSENT_REQUIRED",
            "C_BOOTSTRAP_TRUST_REQUIRED",
        ).forEach { code ->
            validationState(code) shouldBe "operationally_blocked"
        }
    }

    @Test
    fun `suspension and recovery artifact failures keep their protocol-specific class`() {
        validationState("C_RELAY_KEY_MISMATCH") shouldBe "chain_suspended"
        validationState("C_RECOVERY_SHARE_COMPROMISED") shouldBe "chain_suspended"
        validationState("C_RECOVERY_SAS_MISMATCH") shouldBe "recovery_session_failed"
        validationState("C_RECOVERY_SHARE_INVALID") shouldBe "recovery_artifact_quarantined"
        validationState("C_RECOVERY_BLOB_INVALID") shouldBe "recovery_artifact_quarantined"
        validationState("C_SAFETY_LIMIT_REACHED") shouldBe "safety_limited"
        validationState("C_RECOVERY_ROTATION_INCOMPLETE") shouldBe "recovery_rotation_incomplete"
    }

    @Test
    fun `cryptographic integrity errors remain quarantined`() {
        validationState("C_SIGNATURE_INVALID") shouldBe "quarantined"
        validationState("C_AEAD_FAILURE") shouldBe "quarantined"
        validationState("C_LOG_HASH_MISMATCH") shouldBe "quarantined"
    }

    @Test
    fun `classified non-applied inbox rows retain exact reason without becoming corrupt quarantine`() {
        listOf(
            "C_ACCEPTANCE_UNPROVEN",
            "C_UNSUPPORTED_SCHEMA",
            "C_LOCAL_STORAGE_FULL",
            "C_ORDER_KEY_EXHAUSTED",
            "C_RECOVERY_SHARE_INVALID",
            "C_SAFETY_LIMIT_REACHED",
        ).forEachIndexed { index, code ->
            val disposition = validationState(code)
            val inbox =
                PallaSyncInboxEntity(
                    chainId = "chain",
                    recordId = "record-$index",
                    relaySeq = index.toLong() + 1,
                    status = disposition,
                    rawRecordJson = "{}",
                    quarantineReason = code,
                )

            inbox.status shouldBe disposition
            inbox.quarantineReason shouldBe code
        }
    }
}
