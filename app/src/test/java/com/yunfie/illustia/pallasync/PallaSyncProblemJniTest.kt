package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncProblemJniTest {
    @Test
    fun `validated quota context reaches the capacity retry workflow as untrusted diagnostics`() {
        val body =
            """{"type":"about:blank","title":"Insufficient Storage","status":507,"code":"QUOTA_EXCEEDED","retryable":true,"server_time_ms":1791684000000,"context":{"used_bytes":900,"max_bytes":1000}}"""
        val problem = Json.decodeFromString<PallaSyncProblemHint>(PallaSyncCore.validateProblem(body.toByteArray(), 507))

        problem.code shouldBe "QUOTA_EXCEEDED"
        problem.usedBytes shouldBe 900
        problem.maxBytes shouldBe 1000
        problem.retryAfterMs shouldBe null
    }

    @Test
    fun `unvalidated problem bodies never supply retry semantics`() {
        val body = """{"status":403,"code":"DEVICE_PENDING_KEYS","retryable":true}"""

        parsePallaSyncProblem(403, "application/problem+json", body.toByteArray()) shouldBe null
        parsePallaSyncProblem(403, "text/html", "<html>403</html>".toByteArray()) shouldBe null
        classifyPallaSyncHttpStatus(
            403,
            parsePallaSyncProblem(403, "application/problem+json", body.toByteArray()),
        ) shouldBe PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION", 403)
    }

    @Test
    fun `native problem validator matches every normative server code and rejects altered status flags`() {
        val cases = normativeCatalogue()
        cases.size shouldBe 66
        cases.forEach { entry ->
            val valid = problem(entry.status, entry.code, entry.retryable)
            val parsed = Json.parseToJsonElement(PallaSyncCore.validateProblem(valid.toByteArray(), entry.status))
            Json.decodeFromJsonElement<PallaSyncProblemHint>(parsed) shouldBe
                PallaSyncProblemHint(
                    entry.status,
                    entry.code,
                    entry.retryable,
                    serverTimeMs = 1_791_684_000_000L,
                )

            val wrongStatus = problem(entry.status + 1, entry.code, entry.retryable)
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateProblem(wrongStatus.toByteArray(), entry.status)
            }.message shouldBe "C_SCHEMA_VIOLATION"

            val wrongRetryability = problem(entry.status, entry.code, !entry.retryable)
            shouldThrow<IllegalStateException> {
                PallaSyncCore.validateProblem(wrongRetryability.toByteArray(), entry.status)
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
    }

    private data class CatalogueEntry(
        val status: Int,
        val code: String,
        val retryable: Boolean,
    )

    private fun normativeCatalogue(): List<CatalogueEntry> =
        buildList {
            fun add(
                status: Int,
                retryable: Boolean,
                vararg codes: String,
            ) {
                codes.forEach { this@buildList.add(CatalogueEntry(status, it, retryable)) }
            }
            add(400, false, "INVALID_JSON", "INVALID_FIELD", "CURSOR_INVALID", "FILTER_NOT_ALLOWED")
            add(401, true, "TOKEN_REQUIRED", "TOKEN_NOT_YET_VALID", "TOKEN_EXPIRED", "TOKEN_REPLAYED")
            add(401, false, "TOKEN_TTL_TOO_LONG", "TOKEN_SIGNATURE_INVALID", "TOKEN_BINDING_MISMATCH")
            add(403, true, "ADMIN_REQUIRED", "DEVICE_PENDING_KEYS", "DEVICE_UNKNOWN", "ENROLLMENT_NOT_ALLOWED", "ROTATION_NOT_ALLOWED")
            add(403, false, "DEVICE_REVOKED", "DEVICE_KEY_MISMATCH", "ENVELOPE_ACCESS_DENIED")
            add(404, true, "CHAIN_NOT_FOUND", "DEVICE_NOT_FOUND", "EPOCH_NOT_FOUND", "OFFER_NOT_FOUND", "ENVELOPE_NOT_FOUND")
            add(404, true, "SNAPSHOT_PART_NOT_FOUND", "SNAPSHOT_NOT_FOUND", "RECEIPT_NOT_FOUND")
            add(404, false, "ENDPOINT_NOT_FOUND")
            add(405, false, "METHOD_NOT_ALLOWED")
            add(408, true, "REQUEST_TIMEOUT")
            add(409, true, "EPOCH_STALE", "CAS_MISMATCH", "GENERATION_MISMATCH", "MEMBER_SET_MISMATCH")
            add(409, true, "RECOVERY_ENVELOPE_REQUIRED", "HISTORY_KEY_EXPIRED", "CURSOR_EXPIRED", "LOG_ID_MISMATCH")
            add(409, true, "LOG_PRUNED", "SNAPSHOT_INCOMPLETE", "COMPACTION_UNSAFE", "REPAIR_PROOF_REQUIRED")
            add(409, true, "TRANSPARENCY_ID_MISMATCH", "TRANSPARENCY_RANGE_UNAVAILABLE")
            add(409, false, "CHAIN_EXISTS", "RECORD_ID_CONFLICT", "DEVICE_ID_CONFLICT", "REQUEST_ID_CONFLICT")
            add(409, false, "ENVELOPE_ID_CONFLICT", "OPERATION_ID_CONFLICT", "IMPORT_CONFLICT")
            add(410, false, "CHAIN_DELETED")
            add(413, true, "PAYLOAD_TOO_LARGE", "DEVICE_LIMIT_REACHED")
            add(415, false, "UNSUPPORTED_MEDIA_TYPE")
            add(422, false, "OBJECT_SIGNATURE_INVALID", "ENROLLMENT_CERTIFICATE_INVALID", "EPOCH_CHAIN_INVALID")
            add(429, true, "RATE_LIMITED")
            add(500, true, "INTERNAL_ERROR")
            add(502, true, "BAD_GATEWAY")
            add(503, true, "RELAY_UNAVAILABLE", "RELAY_SHUTTING_DOWN")
            add(504, true, "GATEWAY_TIMEOUT")
            add(507, true, "QUOTA_EXCEEDED", "STORAGE_UNAVAILABLE")
        }

    private fun problem(
        status: Int,
        code: String,
        retryable: Boolean,
    ): String =
        """{"type":"about:blank","title":"Problem","status":$status,"code":"$code","retryable":$retryable,"server_time_ms":1791684000000}"""
}
