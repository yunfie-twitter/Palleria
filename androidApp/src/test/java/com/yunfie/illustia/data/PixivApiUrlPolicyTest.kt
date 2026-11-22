package com.yunfie.illustia.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PixivApiUrlPolicyTest :
    StringSpec({
        "accepts canonical Pixiv API pagination URLs" {
            val url =
                "https://app-api.pixiv.net/v1/illust/recommended?offset=30"
                    .toTrustedPixivApiUrl()

            url.host shouldBe "app-api.pixiv.net"
            url.isHttps shouldBe true
        }

        "rejects a response-controlled foreign origin" {
            shouldThrow<IllegalArgumentException> {
                "https://attacker.example/collect".toTrustedPixivApiUrl()
            }
        }

        "rejects plaintext and non-default ports" {
            shouldThrow<IllegalArgumentException> {
                "http://app-api.pixiv.net/v1/illust/recommended".toTrustedPixivApiUrl()
            }
            shouldThrow<IllegalArgumentException> {
                "https://app-api.pixiv.net:8443/v1/illust/recommended".toTrustedPixivApiUrl()
            }
        }

        "rejects URLs containing user information" {
            shouldThrow<IllegalArgumentException> {
                "https://app-api.pixiv.net@attacker.example/collect".toTrustedPixivApiUrl()
            }
        }
    })
