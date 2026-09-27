package com.yunfie.illustia.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LoginUrlPolicyTest :
    StringSpec({
        "accepts the two Pixiv callback routes and decodes the code" {
            pixivLoginCodeOrNull("pixiv://account/login?code=abc%2Bdef") shouldBe "abc+def"
            pixivLoginCodeOrNull("https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback?code=abc") shouldBe "abc"
        }

        "rejects foreign origins and unrelated Pixiv routes" {
            listOf(
                "https://pixiv.example/login?code=abc",
                "https://app-api.pixiv.net.example/web/v1/users/auth/pixiv/callback?code=abc",
                "https://app-api.pixiv.net/other?code=abc",
                "http://app-api.pixiv.net/web/v1/users/auth/pixiv/callback?code=abc",
                "https://app-api.pixiv.net:8443/web/v1/users/auth/pixiv/callback?code=abc",
                "pixiv://other/login?code=abc",
                "pixiv://user@account/login?code=abc",
                "pixiv://account:123/login?code=abc",
            ).forEach { pixivLoginCodeOrNull(it) shouldBe null }
        }

        "rejects malformed and ambiguous callbacks without throwing" {
            listOf(
                "javascript:alert(1)",
                "mailto:example@example.com?code=abc",
                "pixiv://account/login",
                "pixiv://account/login?code=",
                "pixiv://account/login?code=%20",
                "pixiv://account/login?code=%ZZ",
                "pixiv://account/login?code=one&code=two",
                "pixiv://account/login?code=abc#fragment",
            ).forEach { pixivLoginCodeOrNull(it) shouldBe null }
        }

        "accepts Discord app routes only on the secure Discord origin" {
            listOf(
                "https://discord.com/app",
                "https://discord.com/channels/@me",
                "https://discord.com/channels/@me/123",
                "https://discord.com:443/channels/@me?test=true",
            ).forEach { isDiscordAppUrl(it) shouldBe true }
        }

        "rejects lookalike Discord URLs and unrelated pages" {
            listOf(
                null,
                "https://example.com/app",
                "https://example.com/discord.com/app",
                "https://discord.com.example/channels/@me",
                "https://discord.com/app-other",
                "https://discord.com/channels/@member",
                "https://discord.com/login",
                "http://discord.com/app",
                "https://discord.com:8443/app",
                "https://user@discord.com/app",
                "javascript:alert(1)",
            ).forEach { isDiscordAppUrl(it) shouldBe false }
        }
    })
