package com.yunfie.illustia.settings.store

import com.yunfie.illustia.models.NovelReadingProgress
import com.yunfie.illustia.models.NovelReadingStatus
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.StoredAccount
import com.yunfie.illustia.settings.db.ViewHistoryEntity
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreCodecTest {
    @Test
    fun `enumValueOrDefault returns correct enum for valid name and fallback for invalid or null`() {
        enumValueOrDefault("DateDesc", SearchSort.PopularDesc) shouldBe SearchSort.DateDesc
        enumValueOrDefault("PopularDesc", SearchSort.DateDesc) shouldBe SearchSort.PopularDesc
        enumValueOrDefault("invalid_value", SearchSort.DateDesc) shouldBe SearchSort.DateDesc
        enumValueOrDefault(null, SearchSort.DateDesc) shouldBe SearchSort.DateDesc
    }

    @Test
    fun `encodeStringList and decodeStringList roundtrip correctly`() {
        val list = listOf("apple", "banana", "cherry")
        val encoded = encodeStringList(list)
        val decoded = decodeStringList(encoded)
        decoded shouldContainExactly list
    }

    @Test
    fun `decodeStringList handles blank, null, and empty string gracefully`() {
        decodeStringList(null) shouldBe emptyList()
        decodeStringList("") shouldBe emptyList()
        decodeStringList("   ") shouldBe emptyList()
        decodeStringList("[]") shouldBe emptyList()
    }

    @Test
    fun `decodeStringList filters out blank items`() {
        val json = "[\"first\", \"\", \"  \", \"second\"]"
        val decoded = decodeStringList(json)
        decoded shouldContainExactly listOf("first", "second")
    }

    @Test
    fun `decodeStringList falls back to legacy separator when JSON parse fails`() {
        val legacy = "tag1${HISTORY_SEPARATOR}tag2${HISTORY_SEPARATOR}tag3"
        val decoded = decodeStringList(legacy)
        decoded shouldContainExactly listOf("tag1", "tag2", "tag3")
    }

    @Test
    fun `encodeLongList and decodeLongList roundtrip correctly`() {
        val ids = listOf(100L, 200L, 300L)
        val encoded = encodeLongList(ids)
        encoded shouldBe "[100,200,300]"
        val decoded = decodeLongList(encoded)
        decoded shouldContainExactly ids
    }

    @Test
    fun `decodeLongList parses bracketed and unbracketed strings, ignoring invalid or non-positive IDs`() {
        decodeLongList("[10, -5, 0, abc, 20]") shouldContainExactly listOf(10L, 20L)
        decodeLongList("30, 40, 50") shouldContainExactly listOf(30L, 40L, 50L)
        decodeLongList(null) shouldBe emptyList()
        decodeLongList("") shouldBe emptyList()
        decodeLongList("[]") shouldBe emptyList()
    }

    @Test
    fun `encodeAccountTokens and decodeAccountTokens roundtrip correctly`() {
        val accounts =
            listOf(
                StoredAccount(
                    name = "User A",
                    account = "usera",
                    profileImageUrl = null,
                    userId = 1001L,
                    refreshToken = "token_a",
                ),
                StoredAccount(
                    name = "User B",
                    account = "userb",
                    profileImageUrl = null,
                    userId = 1002L,
                    refreshToken = "token_b",
                ),
            )
        val encoded = encodeAccountTokens(accounts)
        val decoded = decodeAccountTokens(encoded)

        decoded shouldBe mapOf(1001L to "token_a", 1002L to "token_b")
        decodeAccountTokens("") shouldBe emptyMap()
        decodeAccountTokens("invalid json") shouldBe emptyMap()
    }

    @Test
    fun `decodeAccounts parses JSON array format correctly`() {
        val accountsJson =
            """
            [
                {
                    "name": "Alice",
                    "account": "alice_pixiv",
                    "profileImageUrl": "https://example.com/alice.jpg",
                    "refreshToken": "tok_1",
                    "userId": 5001
                },
                {
                    "name": "Bob",
                    "account": "bob_pixiv",
                    "profileImageUrl": null,
                    "refreshToken": "tok_2",
                    "userId": 5002
                }
            ]
            """.trimIndent()

        val decoded = decodeAccounts(accountsJson)
        decoded shouldHaveSize 2
        decoded[0].name shouldBe "Alice"
        decoded[0].userId shouldBe 5001L
        decoded[0].profileImageUrl shouldBe "https://example.com/alice.jpg"
        decoded[1].name shouldBe "Bob"
        decoded[1].userId shouldBe 5002L
        decoded[1].profileImageUrl.shouldBeNull()
    }

    @Test
    fun `decodeAccounts ignores entries with missing or non-positive userId`() {
        val json = """[{"name":"NoUser","userId":0},{"name":"Valid","userId":10}]"""
        val decoded = decodeAccounts(json)
        decoded shouldHaveSize 1
        decoded[0].name shouldBe "Valid"
    }

    @Test
    fun `illustFromEntity maps all entity fields into Illust properly`() {
        val entity =
            ViewHistoryEntity(
                9999L,
                "Artwork Title",
                "Artist X",
                "https://example.com/artwork.jpg",
                4,
                "manga",
                0,
                true,
                0,
                "[\"cat\", \"dog\"]",
                1,
            )
        val illust = illustFromEntity(entity)
        illust.id shouldBe 9999L
        illust.title shouldBe "Artwork Title"
        illust.artistName shouldBe "Artist X"
        illust.imageUrl shouldBe "https://example.com/artwork.jpg"
        illust.pageCount shouldBe 4
        illust.type shouldBe "manga"
        illust.isBookmarked shouldBe true
        illust.tags shouldContainExactly listOf("cat", "dog")
        illust.illustAiType shouldBe 1
    }

    @Test
    fun `historyIllustFromJson creates Illust or returns null for invalid items`() {
        historyIllustFromJson(JSONObject()) shouldBe null

        val validJson =
            JSONObject().apply {
                put("id", 12345L)
                put("title", "JSON Illust")
                put("artistName", "Painter")
                put("imageUrl", "https://example.com/json.jpg")
                put("pageCount", 2)
            }
        val illust = historyIllustFromJson(validJson)
        illust.shouldNotBeNull()
        illust.id shouldBe 12345L
        illust.title shouldBe "JSON Illust"
        illust.artistName shouldBe "Painter"
        illust.imageUrl shouldBe "https://example.com/json.jpg"
        illust.pageCount shouldBe 2
    }

    @Test
    fun `encodeNovelProgress and decodeNovelProgress roundtrip correctly`() {
        val progressMap =
            mapOf(
                101L to
                    NovelReadingProgress(
                        novelId = 101L,
                        lastReadPage = 5,
                        totalPages = 20,
                        updatedAt = 123456789L,
                        status = NovelReadingStatus.Reading,
                    ),
                102L to
                    NovelReadingProgress(
                        novelId = 102L,
                        lastReadPage = 10,
                        totalPages = 10,
                        updatedAt = 987654321L,
                        status = NovelReadingStatus.Completed,
                    ),
            )

        val encoded = encodeNovelProgress(progressMap)
        val decoded = decodeNovelProgress(encoded)

        decoded shouldBe progressMap
        decodeNovelProgress(null) shouldBe emptyMap()
        decodeNovelProgress("") shouldBe emptyMap()
        decodeNovelProgress("invalid") shouldBe emptyMap()
    }

    @Test
    fun `encodeStringMap and decodeStringMap roundtrip correctly`() {
        val map = mapOf("key1" to "val1", "key2" to "val2 with spaces and symbols: %&#")
        val encoded = encodeStringMap(map)
        val decoded = decodeStringMap(encoded)

        decoded shouldBe map
        decodeStringMap(null) shouldBe emptyMap()
        decodeStringMap("") shouldBe emptyMap()
        decodeStringMap("{malformed json") shouldBe emptyMap()
    }
}
