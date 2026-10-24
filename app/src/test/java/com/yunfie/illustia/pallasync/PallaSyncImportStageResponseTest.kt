package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncImportStageResponseTest {
    @Test
    fun `accepts only the canonical staging acknowledgement for the selected chain`() {
        PallaSyncImportStageResponse.validate(
            """{"chain_id":"$CHAIN_ID","log_id":"$LOG_ID","state":"staged"}""".toByteArray(),
            CHAIN_ID,
        ) shouldBe PallaSyncImportStageResponse.Staged(CHAIN_ID, LOG_ID)
    }

    @Test
    fun `rejects a different chain or a non-staged state`() {
        listOf(
            """{"chain_id":"different","log_id":"$LOG_ID","state":"staged"}""",
            """{"chain_id":"$CHAIN_ID","log_id":"$LOG_ID","state":"active"}""",
        ).forEach { body ->
            shouldThrow<IllegalStateException> {
                PallaSyncImportStageResponse.validate(body.toByteArray(), CHAIN_ID)
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
    }

    @Test
    fun `rejects unknown members invalid log ids and noncanonical JSON`() {
        listOf(
            """{"chain_id":"$CHAIN_ID","log_id":"$LOG_ID","state":"staged","head_seq":0}""",
            """{"chain_id":"$CHAIN_ID","log_id":"not-a-uuid","state":"staged"}""",
            """{ "chain_id":"$CHAIN_ID","log_id":"$LOG_ID","state":"staged"}""",
        ).forEach { body ->
            shouldThrow<IllegalStateException> {
                PallaSyncImportStageResponse.validate(body.toByteArray(), CHAIN_ID)
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
    }

    private companion object {
        const val CHAIN_ID = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
        const val LOG_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
    }
}
