package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class PallaSyncResponseBodyTest :
    StringSpec({
        fun response(body: ByteArray) =
            Response
                .Builder()
                .request(Request.Builder().url("https://relay.example.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        "the decoded stream stops at the bound before creating an oversized byte array" {
            response(ByteArray(1025)).use {
                shouldThrow<IllegalStateException> { it.pallaSyncBytes(1024) }.message shouldBe "C_LOCAL_LIMIT"
            }
            response(ByteArray(1024)).use { it.pallaSyncBytes(1024).size shouldBe 1024 }
        }
        "invalid UTF8 is never replaced before wire validation" {
            response(byteArrayOf(0xc3.toByte(), 0x28)).use {
                shouldThrow<java.nio.charset.CharacterCodingException> { it.pallaSyncText() }
            }
        }
    })
