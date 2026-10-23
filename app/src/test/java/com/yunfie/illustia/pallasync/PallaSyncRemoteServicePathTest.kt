package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    @Test
    fun `historical source scope allows only reads of the exact chain at the exact origin`() {
        val scope = PallaSyncRemoteService.HistoricalSourceRead(CHAIN_ID, "https://source.example")
        val allowed =
            Request
                .Builder()
                .url("https://source.example/pallasync/v3/chains/$CHAIN_ID/log")
                .get()
                .build()
        val wrongChain =
            Request
                .Builder()
                .url("https://source.example/pallasync/v3/chains/another-chain/log")
                .get()
                .build()
        val wrongOrigin =
            Request
                .Builder()
                .url("https://target.example/pallasync/v3/chains/$CHAIN_ID/log")
                .get()
                .build()
        val mutation =
            Request
                .Builder()
                .url("https://source.example/pallasync/v3/chains/$CHAIN_ID/records")
                .post("{}".toRequestBody())
                .build()

        PallaSyncRemoteService.allowsHistoricalSourceRead(allowed, scope) shouldBe true
        PallaSyncRemoteService.allowsHistoricalSourceRead(wrongChain, scope) shouldBe false
        PallaSyncRemoteService.allowsHistoricalSourceRead(wrongOrigin, scope) shouldBe false
        PallaSyncRemoteService.allowsHistoricalSourceRead(mutation, scope) shouldBe false
    }

    @Test
    fun `migration destination allows authenticated log-head bootstrap but no data log reads or writes`() {
        val targetHead =
            Request
                .Builder()
                .url("https://target.example/pallasync/v3/chains/$CHAIN_ID/log/head")
                .get()
                .build()
        val targetLog =
            Request
                .Builder()
                .url("https://target.example/pallasync/v3/chains/$CHAIN_ID/log?after=0&limit=1")
                .get()
                .build()
        val targetWrite =
            Request
                .Builder()
                .url("https://target.example/pallasync/v3/chains/$CHAIN_ID/records")
                .post("{}".toRequestBody())
                .build()
        val otherChain =
            Request
                .Builder()
                .url("https://target.example/pallasync/v3/chains/other/log/head")
                .get()
                .build()

        PallaSyncRemoteService.allowsMigrationDestination(targetHead, CHAIN_ID) shouldBe true
        PallaSyncRemoteService.allowsMigrationDestination(targetLog, CHAIN_ID) shouldBe false
        PallaSyncRemoteService.allowsMigrationDestination(targetWrite, CHAIN_ID) shouldBe false
        PallaSyncRemoteService.allowsMigrationDestination(otherChain, CHAIN_ID) shouldBe false
    }

    private companion object {
        const val CHAIN_ID = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
    }
}
