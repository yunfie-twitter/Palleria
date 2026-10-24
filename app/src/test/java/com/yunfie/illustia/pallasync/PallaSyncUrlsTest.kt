package com.yunfie.illustia.pallasync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okhttp3.HttpUrl

class PallaSyncUrlsTest :
    StringSpec({
        "normalization accepts a relay origin and builds only the v3 API path" {
            val normalized = normalizedUrl("  https://example.com///  ")

            normalized.toString() shouldBe "https://example.com/"
            PallaSyncUrls.records(normalized, "chain", null, 42L, 999).toString() shouldBe
                "https://example.com/pallasync/v3/chains/chain/records?limit=500&after_seq=42"
        }

        "path values are encoded as segments rather than concatenated" {
            val base = normalizedUrl("https://example.com")

            PallaSyncUrls.recordsEndpoint(base, "a/b").toString() shouldBe
                "https://example.com/pallasync/v3/chains/a%2Fb/records"
            PallaSyncUrls.deletion(base, "a/b").toString() shouldBe
                "https://example.com/pallasync/v3/chains/a%2Fb/deletion"
        }

        "query fragment credentials and unsupported schemes are rejected" {
            listOf(
                "https://example.com?x=1",
                "https://example.com/#fragment",
                "https://user:password@example.com",
                "ftp://example.com",
                "http://example.com",
                "https://example.com/pallasync/v3",
                "   ",
            ).forEach { raw ->
                (PallaSyncUrls.normalize(raw) is PallaSyncHttpResult.ProtocolError) shouldBe true
            }
        }

        "device pagination includes the cursor in the signed query" {
            val url = PallaSyncUrls.devices(normalizedUrl("https://example.com"), "chain", "a_b", 8)
            url.encodedPath shouldBe "/pallasync/v3/chains/chain/devices"
            url.encodedQuery shouldBe "limit=8&cursor=a_b"
        }

        "device export always excludes recovery envelopes and signs its cursor query" {
            val base = normalizedUrl("https://example.com")

            PallaSyncUrls.export(base, "chain", null).encodedQuery shouldBe "limit=200&include_recovery=false"
            PallaSyncUrls.export(base, "chain", "a_b", 1_000).encodedQuery shouldBe
                "limit=1000&include_recovery=false&cursor=a_b"
            PallaSyncUrls.export(base, "chain", null, includeRecovery = true).encodedQuery shouldBe
                "limit=200&include_recovery=true"
        }

        "relay shutdown notice route is chain scoped and has no query" {
            val url = PallaSyncUrls.relayNotice(normalizedUrl("https://example.com"), "chain")
            url.encodedPath shouldBe "/pallasync/v3/chains/chain/relay-notice"
            url.encodedQuery shouldBe null
        }
    })

private fun normalizedUrl(raw: String): HttpUrl =
    when (val result = PallaSyncUrls.normalize(raw)) {
        is PallaSyncHttpResult.Success -> result.value
        else -> error("Expected a valid URL: $raw")
    }
