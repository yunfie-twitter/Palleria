package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncLogPruneRelayTest {
    @Test
    fun `prune response must advance exactly past the authorized prefix`() {
        val response = """{"compaction_id":"c","min_available_seq":43}"""
        val parsed = PallaSyncLogPruneRelay.validatePruneResponse(response.toByteArray(), "c", 42)

        (parsed as PallaSyncHttpResult.Success).value["min_available_seq"].toString() shouldBe "43"
    }

    @Test
    fun `prune response rejects changed prefix and extra fields`() {
        val responses =
            listOf(
                """{"compaction_id":"c","min_available_seq":44}""",
                """{"compaction_id":"c","min_available_seq":43,"head_seq":43}""",
                """{"compaction_id":"c","min_available_seq":"43"}""",
                """{"compaction_id":"c","compaction_id":"c","min_available_seq":43}""",
            )
        responses.forEach { response ->
            shouldThrow<IllegalStateException> {
                PallaSyncLogPruneRelay.validatePruneResponse(response.toByteArray(), "c", 42)
            }
        }
    }
}
