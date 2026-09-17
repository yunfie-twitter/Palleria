package com.yunfie.illustia.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SettingsStorePrivacyModePropertyTest {
    private val allowedChars =
        listOf(
            '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            '+',
            '-',
            '*',
            '×',
            '/',
            '÷',
            '.',
            '=',
        )

    private val context: Context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var settingsStore: SettingsStore

    @BeforeEach
    fun setup() {
        settingsStore = SettingsStore(context)
        settingsStore.clearUnlockCodeHash()
    }

    @Test
    fun `Property 1 解除コードのラウンドトリップ保存`() =
        runBlocking {
            repeat(100) {
                val code = randomCode(Random.nextInt(4, 21))
                settingsStore.clearUnlockCodeHash()
                settingsStore.saveUnlockCodeHash(code)

                settingsStore.hasUnlockCodeSet().shouldBeTrue()
                settingsStore.verifyUnlockCode(code).shouldBeTrue()
            }
        }

    @Test
    fun `Property 2 解除コードの非衝突性`() =
        runBlocking {
            repeat(100) {
                val firstCode = randomCode(Random.nextInt(4, 21))
                var secondCode = randomCode(Random.nextInt(4, 21))
                while (secondCode == firstCode) {
                    secondCode = randomCode(Random.nextInt(4, 21))
                }
                settingsStore.clearUnlockCodeHash()
                settingsStore.saveUnlockCodeHash(firstCode)

                settingsStore.verifyUnlockCode(secondCode).shouldBeFalse()
            }
        }

    @Test
    fun `Property 6 解除コードの長さバリデーション`() {
        repeat(100) {
            val length = if (it % 2 == 0) Random.nextInt(0, 4) else Random.nextInt(21, 31)
            val code = randomCode(length)
            settingsStore.isValidUnlockCode(code).shouldBeFalse()
        }
    }

    private fun randomCode(length: Int): String =
        buildString(length) {
            repeat(length) {
                append(allowedChars.random())
            }
        }
}
