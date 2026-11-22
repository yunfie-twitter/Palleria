package com.yunfie.illustia.data

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class PixivImageProxyTest :
    FreeSpec({
        "proxyPixivImageUrl should rewrite pixiv image hosts to Palleria workers and webp proxy" {
            proxyPixivImageUrl(
                "https://i.pximg.net/c/600x1200_90/img-master/img/2026/09/28/12345678_p0.jpg",
                PALLERIA_IMAGE_PROXY_BASE_URL,
            ) shouldBe
                "https://media.yunfi.f5.si/image.webp?url=https%3A%2F%2Fpximg.2wf6bfhvwk.workers.dev%2Fc%2F600x1200_90%2Fimg-master%2Fimg%2F2026%2F09%2F28%2F12345678_p0.jpg"
        }

        "already converted WebP URLs should not be wrapped again" {
            val converted =
                "https://media.yunfi.f5.si/image.webp?url=" +
                    "https://pximg.2wf6bfhvwk.workers.dev/img-master/img/2021/03/15/15/06/12/88468474_p0_master1200.jpg"
            proxyPixivImageUrl(converted, PALLERIA_IMAGE_PROXY_BASE_URL) shouldBe converted
        }

        "the nested source query should remain encoded inside the WebP url parameter" {
            proxyPixivImageUrl(
                "https://i-f.pximg.net/img-original/example.jpg?first=one&second=two",
                PALLERIA_IMAGE_PROXY_BASE_URL,
            ) shouldBe
                "https://media.yunfi.f5.si/image.webp?url=" +
                "https%3A%2F%2Fpximg.2wf6bfhvwk.workers.dev%2Fimg-original%2Fexample.jpg%3Ffirst%3Done%26second%3Dtwo"
        }

        "proxyPixivImageUrl should rewrite pixiv image hosts to other third-party proxy hosts" {
            proxyPixivImageUrl(
                "https://i.pximg.net/img-original/img/2024/01/01/00/00/00/12345678_p0.jpg",
                "https://i.suimoe.com/",
            ) shouldBe "https://i.suimoe.com/img-original/img/2024/01/01/00/00/00/12345678_p0.jpg"
        }

        "proxyPixivImageUrl should leave non-pixiv image hosts unchanged" {
            proxyPixivImageUrl(
                "https://example.com/image.jpg",
                PALLERIA_IMAGE_PROXY_BASE_URL,
            ) shouldBe "https://example.com/image.jpg"
        }

        "normalizePixivImageProxyBaseUrl should preserve and normalize Palleria URL" {
            normalizePixivImageProxyBaseUrl("https://media.yunfi.f5.si/image.webp?url=") shouldBe PALLERIA_IMAGE_PROXY_BASE_URL
            normalizePixivImageProxyBaseUrl("https://i.suimoe.com/") shouldBe "https://i.suimoe.com/"
        }
    })
