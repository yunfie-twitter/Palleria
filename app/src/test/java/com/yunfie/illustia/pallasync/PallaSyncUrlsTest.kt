package com.yunfie.illustia.pallasync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okhttp3.HttpUrl

class PallaSyncUrlsTest :
    StringSpec({
        "normalization trims whitespace and endpoint construction preserves a base path" {
            val normalized = normalizedUrl("  https://example.com/relay///  ")

            normalized.toString() shouldBe "https://example.com/relay"
            PallaSyncUrls.records(normalized, "chain", null, 42L, 999).toString() shouldBe
                "https://example.com/relay/pallasync/v2/chains/chain/records?limit=500&after_seq=42"
        }

        "path values are encoded as segments rather than concatenated" {
            val base = normalizedUrl("https://example.com")

            PallaSyncUrls.recordsEndpoint(base, "a/b").toString() shouldBe
                "https://example.com/pallasync/v2/chains/a%2Fb/records"
        }

        "query fragment credentials and unsupported schemes are rejected" {
            listOf(
                "https://example.com?x=1",
                "https://example.com/#fragment",
                "https://user:password@example.com",
                "ftp://example.com",
                "   ",
            ).forEach { raw ->
                (PallaSyncUrls.normalize(raw) is PallaSyncHttpResult.ProtocolError) shouldBe true
            }
        }
    })

private fun normalizedUrl(raw: String): HttpUrl =
    when (val result = PallaSyncUrls.normalize(raw)) {
        is PallaSyncHttpResult.Success -> result.value
        else -> error("Expected a valid URL: $raw")
    }
