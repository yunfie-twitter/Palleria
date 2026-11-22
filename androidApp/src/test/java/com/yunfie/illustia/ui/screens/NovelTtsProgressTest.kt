package com.yunfie.illustia.ui.screens

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class NovelTtsProgressTest :
    FunSpec({
        test("pause resumes at the current range instead of the paragraph beginning") {
            val progress = NovelTtsProgress()
            val text = "最初の文。続きの文。最後の文。"
            val (id, spoken) = progress.begin(text)
            spoken shouldBe text
            progress.onRangeStart(id, 5, 10)
            progress.pause()
            progress.begin(text).second shouldBe "続きの文。最後の文。"
        }

        test("repeated pauses accumulate offsets relative to each resumed utterance") {
            val progress = NovelTtsProgress()
            val text = "最初の文。続きの文。最後の文。"
            val first = progress.begin(text).first
            progress.onRangeStart(first, 5, 10)
            progress.pause()
            val second = progress.begin(text).first
            progress.onRangeStart(second, 5, 10)
            progress.pause()
            progress.begin(text).second shouldBe "最後の文。"
        }

        test("late callbacks from a paused utterance cannot overwrite resumed progress") {
            val progress = NovelTtsProgress()
            val text = "abcdefghij"
            val oldId = progress.begin(text).first
            progress.onRangeStart(oldId, 3, 5)
            progress.pause()
            progress.isActive(oldId) shouldBe false
            progress.onRangeStart(oldId, 8, 10)
            val newId = progress.begin(text).first
            newId shouldNotBe oldId
            progress.onRangeStart(oldId, 8, 10)
            progress.onRangeStart(newId, 2, 4)
            progress.pause()
            progress.begin(text).second shouldBe "fghij"
        }

        test("paragraph navigation and new novels reset offsets and invalidate old utterances") {
            val progress = NovelTtsProgress()
            val oldId = progress.begin("abcdefghij").first
            progress.onRangeStart(oldId, 5, 8)
            progress.reset()
            val (newId, spoken) = progress.begin("次の段落")
            spoken shouldBe "次の段落"
            newId shouldNotBe oldId
            progress.isActive(oldId) shouldBe false
            progress.isActive(null) shouldBe false
        }

        test("invalid or backwards range callbacks do not corrupt the resume offset") {
            val progress = NovelTtsProgress()
            val id = progress.begin("abcdefghij").first
            progress.onRangeStart(id, 4, 6)
            progress.onRangeStart(id, -1, 2)
            progress.onRangeStart(id, 8, 20)
            progress.onRangeStart(id, 6, 6)
            progress.onRangeStart(id, 0, 2)
            progress.pause()
            progress.begin("abcdefghij").second shouldBe "efghij"
        }

        test("offsets refer to dictionary expanded text and missing ranges never skip text") {
            val progress = NovelTtsProgress()
            val text = NovelTtsTextSanitizer.sanitizeParagraph("月姫は歩いた。", true, mapOf("月姫" to "かぐや"))
            val id = progress.begin(text).first
            progress.onRangeStart(id, 4, text.length)
            progress.pause()
            progress.begin(text).second shouldBe "歩いた。"
            progress.reset()
            progress.begin(text)
            progress.pause()
            progress.begin(text).second shouldBe text
        }
    })
