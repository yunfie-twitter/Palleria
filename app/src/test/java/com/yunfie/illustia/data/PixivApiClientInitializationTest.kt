package com.yunfie.illustia.data

import com.yunfie.illustia.models.NetworkMode
import org.junit.Test

class PixivApiClientInitializationTest {
    @Test
    fun `constructing clients for settings does not require Android or native libraries`() {
        // Plain JVM test: the OkHttp client is created lazily, so constructing the
        // wrapper for settings must not touch Android runtime methods or open sockets.
        NetworkMode.entries.forEach { mode -> PixivApiClient(mode) }
    }
}
