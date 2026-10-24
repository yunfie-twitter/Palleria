package com.yunfie.illustia.pallasync

import com.yunfie.illustia.models.Illust
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

class PallaSyncViewedProjectionTest :
    StringSpec({
        "id-only viewed winners keep existence with unknown metadata" {
            val body = Json.decodeFromString<ViewedIllustBody>("{\"id\":7}")
            body.title shouldBe null
            body.pageCount shouldBe null
            body.isBookmarked shouldBe null
            body.tags shouldBe null
            body.illust().id shouldBe 7L
        }

        "a replacement winner does not inherit metadata from a losing viewed body" {
            val prior = Json.decodeFromString<ViewedIllustBody>("{\"id\":7,\"title\":\"old\",\"tags\":[\"old\"]}")
            val winner = Json.decodeFromString<ViewedIllustBody>("{\"id\":7,\"artistName\":\"new\"}")
            prior.title shouldBe "old"
            winner.title shouldBe null
            winner.tags shouldBe null
            winner.artistName shouldBe "new"
            winner.illust().tags shouldBe emptyList()
        }

        "cache serialization and reaccess never assert placeholder metadata" {
            val body = Json.decodeFromString<ViewedIllustBody>("{\"id\":7}")
            val cached = Json.decodeFromString<Illust>(Json.encodeToString(body.illust()))
            cached.pallaSyncViewedBody().toString() shouldBe "{\"id\":7}"
        }

        "a present metadata value can change while omitted fields stay unknown" {
            val body = Json.decodeFromString<ViewedIllustBody>("{\"id\":7,\"title\":\"old\"}")
            val changed = body.illust().copy(title = "new", isBookmarked = true)
            val result = changed.pallaSyncViewedBody()
            result.keys shouldBe setOf("id", "title")
            result.getValue("title").jsonPrimitive.content shouldBe "new"
        }
    })
