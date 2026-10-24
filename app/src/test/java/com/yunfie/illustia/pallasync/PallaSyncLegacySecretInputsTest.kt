package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncLegacySecretInputsTest {
    @Test
    fun `owned JNI buffers are erased after use`() {
        lateinit var mnemonic: ByteArray
        lateinit var passphrase: ByteArray
        withLegacySecretInputs("secret phrase", "pass phrase") { mnemonicBytes, passphraseBytes ->
            mnemonic = mnemonicBytes
            passphrase = passphraseBytes
            mnemonicBytes.toString(Charsets.UTF_8) shouldBe "secret phrase"
            passphraseBytes.toString(Charsets.UTF_8) shouldBe "pass phrase"
        }
        mnemonic.all { it == 0.toByte() } shouldBe true
        passphrase.all { it == 0.toByte() } shouldBe true
    }

    @Test
    fun `owned JNI buffers are erased when native call fails`() {
        lateinit var mnemonic: ByteArray
        lateinit var passphrase: ByteArray
        shouldThrow<IllegalStateException> {
            withLegacySecretInputs("secret phrase", "pass phrase") { mnemonicBytes, passphraseBytes ->
                mnemonic = mnemonicBytes
                passphrase = passphraseBytes
                error("native failure")
            }
        }
        mnemonic.all { it == 0.toByte() } shouldBe true
        passphrase.all { it == 0.toByte() } shouldBe true
    }

    @Test
    fun `invalid UTF16 is rejected before JNI`() {
        var enteredJni = false
        shouldThrow<IllegalStateException> {
            withLegacySecretInputs("valid phrase", "\uD800") { _, _ ->
                enteredJni = true
            }
        }
        enteredJni shouldBe false
    }
}
