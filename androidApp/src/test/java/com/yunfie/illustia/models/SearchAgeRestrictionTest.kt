package com.yunfie.illustia.models

import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.searchProjection
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class SearchAgeRestrictionTest :
    StringSpec({
        "all is the default and preserves an explicit user query" {
            AppSettings().searchAgeRestriction shouldBe SearchAgeRestriction.All
            SearchAgeRestriction.All.applyToQuery("landscape R-18G") shouldBe "landscape R-18G"
        }

        "rating tag is combined with the query without replacing its other conditions" {
            SearchAgeRestriction.R18.applyToQuery(" landscape 100users入り ") shouldBe "landscape 100users入り R-18"
            SearchAgeRestriction.R18G.applyToQuery("landscape") shouldBe "landscape R-18G"
        }

        "changing the selection does not accumulate tags in the original query" {
            val query = "landscape"
            SearchAgeRestriction.R18.applyToQuery(query) shouldBe "landscape R-18"
            SearchAgeRestriction.R18G.applyToQuery(query) shouldBe "landscape R-18G"
            SearchAgeRestriction.All.applyToQuery(query) shouldBe query
        }

        "search projection tracks rating independently from global content visibility" {
            val settings = AppSettings(allowR18 = false, allowR18G = false, searchAgeRestriction = SearchAgeRestriction.R18G)
            settings.searchProjection().ageRestriction shouldBe SearchAgeRestriction.R18G
            settings.searchProjection().allowR18 shouldBe false
            settings.searchProjection().allowR18G shouldBe false
        }
    })
