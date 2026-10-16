package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class PallaSyncBootstrapReviewTest {
    @Test
    fun `review binds derived identity to verified origin and checkpoint`() {
        val review = PallaSyncBootstrapReview.fromVerifiedCheckpoint(origin, identity, checkpoint())

        review.relayOrigin shouldBe origin
        review.chainId shouldBe "chain-1"
        review.adminPublicKey shouldBe "admin-key"
        review.logId shouldBe "log-1"
        review.generation shouldBe 2L
        review.headSeq shouldBe 17L
        review.headHash shouldBe "history-hash"
        review.parametersHash shouldBe "parameters-hash"
        review.epochHash shouldBe "epoch-hash"
        review.transparencyIndex shouldBe 31L
    }

    @Test
    fun `review rejects a different origin or derived root identity`() {
        shouldThrow<IllegalStateException> {
            PallaSyncBootstrapReview.fromVerifiedCheckpoint("https://other.example", identity, checkpoint())
        }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"

        shouldThrow<IllegalStateException> {
            PallaSyncBootstrapReview.fromVerifiedCheckpoint(
                origin,
                identity.copy(chainId = "other-chain"),
                checkpoint(),
            )
        }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"

        shouldThrow<IllegalStateException> {
            PallaSyncBootstrapReview.fromVerifiedCheckpoint(
                origin,
                identity.copy(adminPublicKey = "other-admin"),
                checkpoint(),
            )
        }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"
    }

    private fun checkpoint() =
        PallaSyncGenesisCheckpoint(
            logId = "log-1",
            headSeq = 17,
            headHash = "history-hash",
            parametersHash = "parameters-hash",
            epochHash = "epoch-hash",
            transparencyId = "transparency-1",
            transparencyIndex = 31,
            transparencyHash = "transparency-hash",
            authority =
                Json
                    .parseToJsonElement(
                        """{"pin":{"origin":"$origin","log_id":"log-1"},"chain_id":"chain-1","admin_public_key":"admin-key","generation":2,"sequence":17,"head_hash":"history-hash"}""",
                    ).jsonObject,
            generation = 2,
            dataZero = "data-zero",
        )

    private companion object {
        const val origin = "https://relay.example"
        val identity = PallaSyncRootSession.Identity("chain-1", "admin-key")
    }
}
