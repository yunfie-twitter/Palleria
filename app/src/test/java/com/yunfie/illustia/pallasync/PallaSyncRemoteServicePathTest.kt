package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncRemoteServicePathTest {
    @Test
    fun `top level import route is not interpreted as a chain id`() {
        PallaSyncRemoteService.chainIdForAuthorization(listOf("pallasync", "v3", "chains", "import")) shouldBe null
    }

    @Test
    fun `chain scoped routes bind their actual chain id`() {
        PallaSyncRemoteService.chainIdForAuthorization(
            listOf("pallasync", "v3", "chains", CHAIN_ID, "parameters"),
        ) shouldBe CHAIN_ID
        PallaSyncRemoteService.chainIdForAuthorization(
            listOf("pallasync", "v3", "chains", CHAIN_ID),
        ) shouldBe CHAIN_ID
    }

    @Test
    fun `unrelated and malformed shallow paths do not acquire a chain binding`() {
        PallaSyncRemoteService.chainIdForAuthorization(listOf("pallasync", "v3", "health")) shouldBe null
        PallaSyncRemoteService.chainIdForAuthorization(listOf("pallasync", "v3", "chains")) shouldBe null
    }

    private companion object {
        const val CHAIN_ID = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
    }
}
