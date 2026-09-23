package com.yunfie.illustia.settings

import com.yunfie.illustia.settings.db.ViewHistoryEntity
import com.yunfie.illustia.settings.store.historyIllustFromJson
import com.yunfie.illustia.settings.store.illustFromEntity
import io.kotest.matchers.shouldBe
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ViewHistoryCodecTest {
    @Test
    fun `view history entity preserves R-18 and R-18G information`() {
        val r18Entity =
            ViewHistoryEntity(
                101L,
                "R18 Illust",
                "Artist",
                "https://example.com/img.jpg",
                1,
                "illust",
                0,
                false,
                1,
                "[\"original\",\"R-18\"]",
                0,
            )
        val decodedFromEntity = illustFromEntity(r18Entity)
        decodedFromEntity.isR18 shouldBe true
        decodedFromEntity.ageRestrictionBadgeText shouldBe "R-18"
        decodedFromEntity.tags shouldBe listOf("original", "R-18")

        val r18GEntity =
            ViewHistoryEntity(
                102L,
                "R18G Illust",
                "Artist",
                "https://example.com/img.jpg",
                1,
                "illust",
                1,
                false,
                2,
                "[\"original\",\"R-18G\"]",
                0,
            )
        val decodedR18G = illustFromEntity(r18GEntity)
        decodedR18G.isR18 shouldBe true
        decodedR18G.isR18G shouldBe true
        decodedR18G.ageRestrictionBadgeText shouldBe "R-18G"
    }

    @Test
    fun `history illust from json preserves R-18 and R-18G and tags`() {
        val json =
            JSONObject().apply {
                put("id", 103L)
                put("title", "R18 JSON")
                put("artistName", "Artist")
                put("imageUrl", "https://example.com/img.jpg")
                put("pageCount", 1)
                put("type", "illust")
                put("xRestrict", 1)
                put("tags", JSONArray().apply { put("R-18") })
            }
        val decodedFromJson = historyIllustFromJson(json)
        decodedFromJson?.isR18 shouldBe true
        decodedFromJson?.ageRestrictionBadgeText shouldBe "R-18"
        decodedFromJson?.tags shouldBe listOf("R-18")
    }
}
