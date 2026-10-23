package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
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
}
