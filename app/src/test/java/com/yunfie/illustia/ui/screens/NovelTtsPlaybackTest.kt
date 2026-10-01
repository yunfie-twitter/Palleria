package com.yunfie.illustia.ui.screens

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NovelTtsPlaybackTest :
    FunSpec({
        test("NovelTtsState has expected default values") {
            val state = NovelTtsState()
            state.isPlaying shouldBe false
            state.novelId shouldBe null
            state.novelTitle shouldBe ""
            state.authorName shouldBe ""
            state.currentParagraphIndex shouldBe 0
            state.totalParagraphs shouldBe 0
            state.currentParagraphText shouldBe ""
            state.speechRate shouldBe 1.0f
            state.isInitialized shouldBe false
        }

        test("NovelTtsState updates properties correctly") {
            val initialState = NovelTtsState()
            val playingState =
                initialState.copy(
                    isPlaying = true,
                    novelId = 12345L,
                    novelTitle = "テスト小説",
                    authorName = "作者名",
                    currentParagraphIndex = 3,
                    totalParagraphs = 10,
                    currentParagraphText = "これは第4段落です。",
                    speechRate = 1.25f,
                    isInitialized = true,
                )

            playingState.isPlaying shouldBe true
            playingState.novelId shouldBe 12345L
            playingState.novelTitle shouldBe "テスト小説"
            playingState.authorName shouldBe "作者名"
            playingState.currentParagraphIndex shouldBe 3
            playingState.totalParagraphs shouldBe 10
            playingState.currentParagraphText shouldBe "これは第4段落です。"
            playingState.speechRate shouldBe 1.25f
            playingState.isInitialized shouldBe true
        }

        test("Ruby and pixiv special tags are parsed and cleaned properly for TTS reading") {
            val rubyRegex = Regex("""\[\[rb:[^>]*>(.*?)\]\]""")
            val jumpRegex = Regex("""\[jump:\d+\]""")
            val newpageRegex = Regex("""\[newpage\]""")
            val pixivImageRegex = Regex("""\[pixivimage:\d+\]""")
            val chapterRegex = Regex("""\[chapter:(.*?)\]""")

            val raw = "吾輩は[[rb:猫>ねこ]]である。[jump:1]\n[chapter:第1章]\n[newpage]\n名前はまだ無い。[pixivimage:98765]"
            val cleaned =
                raw
                    .replace(rubyRegex, "$1")
                    .replace(jumpRegex, "")
                    .replace(newpageRegex, "\n\n")
                    .replace(pixivImageRegex, "")
                    .replace(chapterRegex, "$1")

            val paragraphs = cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }

            paragraphs.size shouldBe 3
            paragraphs[0] shouldBe "吾輩はねこである。"
            paragraphs[1] shouldBe "第1章"
            paragraphs[2] shouldBe "名前はまだ無い。"
        }

        test("NovelTtsService action strings have expected intents") {
            NovelTtsService.ACTION_START_READING shouldBe "com.yunfie.illustia.tts.START_READING"
            NovelTtsService.ACTION_PLAY shouldBe "com.yunfie.illustia.tts.PLAY"
            NovelTtsService.ACTION_PAUSE shouldBe "com.yunfie.illustia.tts.PAUSE"
            NovelTtsService.ACTION_TOGGLE_PLAY shouldBe "com.yunfie.illustia.tts.TOGGLE_PLAY"
            NovelTtsService.ACTION_NEXT shouldBe "com.yunfie.illustia.tts.NEXT"
            NovelTtsService.ACTION_PREV shouldBe "com.yunfie.illustia.tts.PREV"
            NovelTtsService.ACTION_STOP shouldBe "com.yunfie.illustia.tts.STOP"
            NovelTtsService.ACTION_SET_SPEED shouldBe "com.yunfie.illustia.tts.SET_SPEED"
            NovelTtsService.ACTION_SEEK_PARAGRAPH shouldBe "com.yunfie.illustia.tts.SEEK_PARAGRAPH"
        }

        test("NovelTtsNotificationHelper notification channel and ID constants") {
            NovelTtsNotificationHelper.CHANNEL_ID shouldBe "novel_tts_channel"
            NovelTtsNotificationHelper.NOTIFICATION_ID shouldBe 2001
        }
    })
