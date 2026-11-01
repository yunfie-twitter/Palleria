package com.yunfie.illustia.ui.screens.pallasync

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class PallaSyncRootActionTimeoutTest {
    @Test
    fun stopsAnActionThatExceedsItsTimeout() =
        runBlocking {
            val result =
                runCatching {
                    runRootActionWithTimeout(timeoutMillis = 10) {
                        delay(100)
                        true
                    }
                }

            assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
        }
}
