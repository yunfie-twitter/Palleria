package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Base64

/** Cursor state tests use prevalidated placeholder anchors; crypto verification is exercised via JNI/relay tests. */
class PallaSyncTransparencyRangeTest :
    FunSpec({
        test("continuations preserve the range start while the verified anchor advances") {
            val range = range()
            range.url(BASE, CHAIN).queryParameter("after_index") shouldBe "0"
            range.advance(response("first_cursor"), anchor(1))
            range.url(BASE, CHAIN).queryParameter("after_index") shouldBe "0"
            range.url(BASE, CHAIN).queryParameter("cursor") shouldBe "first_cursor"
            range.advance(response("second_cursor"), anchor(2))
            range.url(BASE, CHAIN).queryParameter("after_index") shouldBe "0"
            range.index shouldBe 2L
            range.through shouldBe TARGET
        }

        test("an expired cursor resumes at the latest anchor without replacing the target") {
            val range = range()
            val target = range.head
            range.url(BASE, CHAIN)
            range.advance(response("first_cursor"), anchor(1))
            range.restartExpiredCursor() shouldBe true
            range.url(BASE, CHAIN).queryParameter("after_index") shouldBe "1"
            range.url(BASE, CHAIN).queryParameter("cursor") shouldBe null
            range.restartExpiredCursor() shouldBe false
            range.advance(response("second_cursor"), anchor(2))
            range.restartExpiredCursor() shouldBe true
            range.url(BASE, CHAIN).queryParameter("after_index") shouldBe "2"
            range.head shouldBe target
        }

        test("a persisted anchor restarts without needing its previous opaque cursor") {
            val resumed = PallaSyncTransparencyRange(head(), anchor(2), 1)
            resumed.url(BASE, CHAIN).queryParameter("after_index") shouldBe "2"
            resumed.url(BASE, CHAIN).queryParameter("cursor") shouldBe null
            resumed.hasRemaining shouldBe true
            resumed.advance(response(null), anchor(TARGET))
            resumed.requireComplete()
            resumed.hasRemaining shouldBe false
        }

        test("an incomplete anchor cannot be promoted to the signed target") {
            val range = range()
            shouldThrow<IllegalStateException> { range.requireComplete() }.message shouldBe "C_TRANSPARENCY_INVALID"
        }
    })

private val BASE = "https://relay.example.test".toHttpUrl()
private const val CHAIN = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
private const val TRANSPARENCY = "11111111-1111-4111-8111-111111111111"
private const val TARGET = 3L
private const val HASH_BYTES = 32

private fun hash(index: Long): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(HASH_BYTES) { index.toByte() })

private fun head(): JsonObject =
    buildJsonObject {
        put("entry_index", TARGET)
        put("transparency_id", TRANSPARENCY)
        put("transparency_hash", hash(TARGET))
    }

private fun anchor(index: Long): JsonObject =
    buildJsonObject {
        put("index", index)
        put("transparency_id", TRANSPARENCY)
        put("hash", hash(index))
    }

private fun response(cursor: String?): JsonObject =
    buildJsonObject {
        if (cursor == null) put("next_cursor", JsonNull) else put("next_cursor", cursor)
    }

private fun range(): PallaSyncTransparencyRange = PallaSyncTransparencyRange(head(), anchor(0), 1)
