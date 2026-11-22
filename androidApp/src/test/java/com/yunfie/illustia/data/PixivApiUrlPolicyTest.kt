package com.yunfie.illustia.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PixivApiUrlPolicyTest :
    StringSpec({
        "accepts canonical Pixiv API pagination URLs" {
            val url = trustedPixivApiUrl("https://app-api.pixiv.net/v1/illust/recommended?offset=30")

            url shouldBe "https://app-api.pixiv.net/v1/illust/recommended?offset=30"
        }

        "rejects a response-controlled foreign origin" {
            shouldThrow<IllegalArgumentException> {
                trustedPixivApiUrl("https://attacker.example/collect")
            }
        }

        "rejects plaintext and non-default ports" {
            shouldThrow<IllegalArgumentException> {
                trustedPixivApiUrl("http://app-api.pixiv.net/v1/illust/recommended")
            }
            shouldThrow<IllegalArgumentException> {
                trustedPixivApiUrl("https://app-api.pixiv.net:8443/v1/illust/recommended")
            }
        }

        "rejects URLs containing user information" {
            shouldThrow<IllegalArgumentException> {
                trustedPixivApiUrl("https://app-api.pixiv.net@attacker.example/collect")
            }
        }
    })
