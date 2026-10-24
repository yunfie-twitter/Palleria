package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.security.MessageDigest

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncJcsVectorsJniTest {
    @Test
    fun `Appendix C JCS positive vectors canonicalize and hash through JNI`() {
        val vectors =
            listOf(
                Vector(
                    """{"z":2,"a":1}""",
                    """{"a":1,"z":2}""",
                    "99168216144c7fed5d4c54916cf98d9c66096280c04a499822a99b6658bd177a",
                ),
                Vector(
                    """{"":1,"😀":2,"a":3}""",
                    """{"a":3,"😀":2,"":1}""",
                    "84495633024b0992798250783e7ccb01ab2875649ab22f353f14c0d6a8389fc2",
                ),
                Vector(
                    """{"text":"\u0000\b\t\n\f\r\"\\/"}""",
                    """{"text":"\u0000\b\t\n\f\r\"\\/"}""",
                    "997f29258dcf1c2af2af1cd2e02858e4c93dc4637189d62278a67426a6728fd2",
                ),
                Vector(
                    """{"z":"日本語","a":[true,false,null,9007199254740991]}""",
                    """{"a":[true,false,null,9007199254740991],"z":"日本語"}""",
                    "2eab9e896157ee9c86d967cedb96c09f95b0365051782611ea71351775a6e7ff",
                ),
            )

        vectors.forEach { vector ->
            val canonical = PallaSyncCore.canonicalJson(vector.input)
            canonical shouldBe vector.expected
            canonical!!.toByteArray(Charsets.UTF_8).sha256Hex() shouldBe vector.sha256
        }
    }

    @Test
    fun `Appendix C JCS lexical negatives are rejected before canonicalization`() {
        val invalid =
            listOf(
                """{"a":1,"a":2}""",
                """{"a":-0}""",
                """{"a":1e3}""",
                """{"a":1.0}""",
                """{"a":01}""",
                """{"a":-1}""",
                """{"a":9007199254740992}""",
                """{"a":"\ud800"}""",
                "[".repeat(33) + "0" + "]".repeat(33),
            )

        invalid.forEach { input ->
            PallaSyncCore.canonicalJson(input) shouldBe null
        }
    }

    @Test
    fun `byte oriented JNI parser rejects invalid UTF-8 and a leading BOM`() {
        val invalidBodies =
            listOf(
                byteArrayOf(0xc0.toByte(), 0xaf.toByte()),
                byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + "{}".toByteArray(),
            )

        invalidBodies.forEach { body ->
            runCatching { PallaSyncCore.validateLogHead(body) }.isFailure shouldBe true
        }
    }

    @Test
    fun `native strict base64url decoder rejects nonzero trailing bits`() {
        val nonCanonical32ByteValue = "A".repeat(42) + "B"

        shouldThrow<IllegalStateException> {
            PallaSyncCore.beginLegacyMigrationSession(
                byteArrayOf(),
                byteArrayOf(),
                nonCanonical32ByteValue,
                "",
                "",
            )
        }.message shouldBe "C_SCHEMA_VIOLATION"
    }

    private data class Vector(
        val input: String,
        val expected: String,
        val sha256: String,
    )

    private fun ByteArray.sha256Hex(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }
}
