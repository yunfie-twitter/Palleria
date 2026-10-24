package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** Real JNI handle lifetime, including cancellation before construction and stale screen disposal. */
class PallaSyncRootCancellationJniTest :
    StringSpec({
        fun root(passphrase: String = ""): PallaSyncRootSession {
            val words = (List(23) { "abandon" } + "art").joinToString(" ").toByteArray()
            val input = passphrase.toByteArray()
            return PallaSyncRootSession.begin(words, input, PallaSyncRootSession.Operation.ROTATE).also {
                words.all { it == 0.toByte() } shouldBe true
                input.all { it == 0.toByte() } shouldBe true
            }
        }

        "an old cancelled operation cannot close a newer foreground root session" {
            PallaSyncCore.setSecretForeground(true)
            val old = PallaSyncRootCancellation()
            root().use { first ->
                old.attach(first)
                val defaultIdentity = first.identity
                old.cancel()
                shouldThrow<IllegalStateException> { first.identity }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                old.release(first)
                val current = PallaSyncRootCancellation()
                root(" spaced passphrase é ").use { second ->
                    current.attach(second)
                    val identity = second.identity
                    (identity.chainId != defaultIdentity.chainId) shouldBe true
                    old.cancel()
                    second.identity shouldBe identity
                    current.cancel()
                    shouldThrow<IllegalStateException> { second.identity }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                    current.release(second)
                }
            }
        }

        "cancellation before native construction closes the eventual handle without an authorized operation" {
            PallaSyncCore.setSecretForeground(true)
            val cancelled = PallaSyncRootCancellation()
            cancelled.cancel()
            root().use {
                shouldThrow<IllegalStateException> { cancelled.attach(it) }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                shouldThrow<IllegalStateException> { it.identity }.message shouldBe "C_RECOVERY_AUTH_REQUIRED"
                cancelled.release(it)
            }
        }
    })
