package com.yunfie.illustia.pallasync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class PallaSyncRecoveryGuidanceTest :
    StringSpec({
        "every retryable §19.2 server code has explicit next-step guidance" {
            PallaSyncRecoveryGuidance.retryableServerCodes shouldContainExactlyInAnyOrder
                setOf(
                    "TOKEN_REQUIRED",
                    "TOKEN_NOT_YET_VALID",
                    "TOKEN_EXPIRED",
                    "TOKEN_REPLAYED",
                    "ADMIN_REQUIRED",
                    "DEVICE_PENDING_KEYS",
                    "DEVICE_UNKNOWN",
                    "ENROLLMENT_NOT_ALLOWED",
                    "ROTATION_NOT_ALLOWED",
                    "CHAIN_NOT_FOUND",
                    "DEVICE_NOT_FOUND",
                    "EPOCH_NOT_FOUND",
                    "OFFER_NOT_FOUND",
                    "ENVELOPE_NOT_FOUND",
                    "SNAPSHOT_PART_NOT_FOUND",
                    "SNAPSHOT_NOT_FOUND",
                    "REQUEST_TIMEOUT",
                    "EPOCH_STALE",
                    "CAS_MISMATCH",
                    "GENERATION_MISMATCH",
                    "MEMBER_SET_MISMATCH",
                    "RECOVERY_ENVELOPE_REQUIRED",
                    "HISTORY_KEY_EXPIRED",
                    "CURSOR_EXPIRED",
                    "LOG_ID_MISMATCH",
                    "LOG_PRUNED",
                    "SNAPSHOT_INCOMPLETE",
                    "COMPACTION_UNSAFE",
                    "REPAIR_PROOF_REQUIRED",
                    "PAYLOAD_TOO_LARGE",
                    "DEVICE_LIMIT_REACHED",
                    "RATE_LIMITED",
                    "INTERNAL_ERROR",
                    "BAD_GATEWAY",
                    "RELAY_UNAVAILABLE",
                    "RELAY_SHUTTING_DOWN",
                    "GATEWAY_TIMEOUT",
                    "QUOTA_EXCEEDED",
                    "STORAGE_UNAVAILABLE",
                    "RECEIPT_NOT_FOUND",
                    "TRANSPARENCY_ID_MISMATCH",
                    "TRANSPARENCY_RANGE_UNAVAILABLE",
                )
        }

        "recovery guidance keeps sensitive changes behind their dedicated flows" {
            PallaSyncRecoveryGuidance.forCode("DEVICE_LIMIT_REACHED") shouldBe
                PallaSyncRecoveryHint.DEVICE_SLOT_RELEASE
            PallaSyncRecoveryGuidance.forCode("RELAY_SHUTTING_DOWN") shouldBe
                PallaSyncRecoveryHint.EXPLICIT_RELAY_MIGRATION
            PallaSyncRecoveryGuidance.forCode("RECEIPT_NOT_FOUND") shouldBe
                PallaSyncRecoveryHint.WAIT_FOR_RECEIPT_EVIDENCE
            PallaSyncRecoveryGuidance.forCode("EPOCH_STALE") shouldBe
                PallaSyncRecoveryHint.VERIFY_EPOCH_HISTORY
            PallaSyncRecoveryGuidance.forCode("TOKEN_SIGNATURE_INVALID") shouldBe null
        }
    })
