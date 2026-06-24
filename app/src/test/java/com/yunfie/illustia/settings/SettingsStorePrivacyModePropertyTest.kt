package com.yunfie.illustia.settings

import android.content.Context
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.junit5.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(manifest = Config.NONE)
class SettingsStorePrivacyModePropertyTest {

    private val allowedChars = listOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '+', '-', '*', '×', '/', '÷', '.', '='
    )

    private val validUnlockCodeArb = Arb.string(4..20, allowedChars)
    private val invalidLengthUnlockCodeArb = Arb.choice(
        Arb.string(0..3, allowedChars),
        Arb.string(21..30, allowedChars),
    )

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private lateinit var settingsStore: SettingsStore

    @BeforeEach
    fun setup() {
        settingsStore = SettingsStore(context)
        settingsStore.clearUnlockCodeHash()
    }

    @Test
    fun `Property 1 解除コードのラウンドトリップ保存`() {
        checkAll(
            PropTestConfig(iterations = 100),
            validUnlockCodeArb,
        ) { code ->
            settingsStore.clearUnlockCodeHash()
            settingsStore.saveUnlockCodeHash(code)

            settingsStore.hasUnlockCodeSet().shouldBeTrue()
            settingsStore.verifyUnlockCode(code).shouldBeTrue()
        }
    }

    @Test
    fun `Property 2 解除コードの非衝突性`() {
        checkAll(
            PropTestConfig(iterations = 100),
            validUnlockCodeArb,
            validUnlockCodeArb,
        ) { firstCode, secondCode ->
            if (firstCode == secondCode) return@checkAll

            settingsStore.clearUnlockCodeHash()
            settingsStore.saveUnlockCodeHash(firstCode)

            settingsStore.verifyUnlockCode(secondCode).shouldBeFalse()
        }
    }

    @Test
    fun `Property 6 解除コードの長さバリデーション`() {
        checkAll(
            PropTestConfig(iterations = 100),
            invalidLengthUnlockCodeArb,
        ) { code ->
            settingsStore.isValidUnlockCode(code).shouldBeFalse()
        }
    }
}
