package com.yunfie.illustia.data

import com.yunfie.illustia.models.NetworkMode
import org.junit.Test

class PixivApiClientInitializationTest {
    @Test
    fun `constructing clients for settings does not require Android or native libraries`() {
        // Plain JVM test: neither Android runtime methods nor the Android UniFFI library
        // are available. Native initialization here would fail before any API request.
        NetworkMode.entries.forEach { mode -> PixivApiClient(mode) }
    }
}
