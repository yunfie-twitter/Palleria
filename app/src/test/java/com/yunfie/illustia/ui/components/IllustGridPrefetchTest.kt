package com.yunfie.illustia.ui.components

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class IllustGridPrefetchTest :
    FreeSpec({
        val indices = (0 until 50).associate { "timeline_$it" as Any to it }

        "prefetch follows visible artwork keys and ignores banners" {
            upcomingArtworkIndices(indices, listOf("banner", "timeline_20", "timeline_21"), 50, 12)
                .toList() shouldBe (22..33).toList()
        }

        "scrolling back moves the window back" {
            upcomingArtworkIndices(indices, listOf("timeline_4", "timeline_5"), 50, 12)
                .toList() shouldBe (6..17).toList()
        }

        "sorting uses the new artwork positions rather than grid indices" {
            val sorted = mapOf<Any, Int>(30L to 0, 10L to 1, 20L to 2, 40L to 3)
            upcomingArtworkIndices(sorted, listOf("banner", 10L), 4, 12)
                .toList() shouldBe listOf(2, 3)
        }

        "end of list never prefetches visible or nonexistent artwork" {
            upcomingArtworkIndices(indices, listOf("timeline_47"), 50, 12)
                .toList() shouldBe listOf(48, 49)
            upcomingArtworkIndices(indices, listOf("timeline_49", "loading"), 50, 12)
                .toList() shouldBe emptyList()
        }

        "unmeasured grids and non-artwork rows do not prefetch" {
            upcomingArtworkIndices(indices, emptyList(), 50, 12).toList() shouldBe emptyList()
            upcomingArtworkIndices(indices, listOf("loading"), 50, 12).toList() shouldBe emptyList()
            upcomingArtworkIndices(emptyMap(), listOf("timeline_0"), 0, 12).toList() shouldBe emptyList()
        }
    })
