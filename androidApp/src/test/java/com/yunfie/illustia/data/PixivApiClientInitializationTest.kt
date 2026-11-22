package com.yunfie.illustia.data

import com.yunfie.illustia.models.NetworkMode
import org.junit.Test

class PixivApiClientInitializationTest {
    @Test
    fun `constructing clients for settings does not require Android or native libraries`() {
        // Plain JVM test: the Ktor client is built from a plain OkHttp engine here, so
        // constructing the wrapper for each network mode must not open sockets.
        NetworkMode.entries.forEach { mode ->
            PixivApiClient(createKtorPixivClient(createPixivHttpClient(mode)))
        }
    }
}
