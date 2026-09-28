package com.yunfie.illustia.ui.screens

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

private val RUBY_REGEX = Regex("""\[\[rb:[^>]*>(.*?)\]\]""")
private val JUMP_REGEX = Regex("""\[jump:\d+\]""")
private val NEWPAGE_REGEX = Regex("""\[newpage\]""")
private val PIXIV_IMAGE_REGEX = Regex("""\[pixivimage:\d+\]""")
private val CHAPTER_REGEX = Regex("""\[chapter:(.*?)\]""")

class NovelTtsPlayer(
    private val context: Context,
    private val onPageAdvance: ((Int) -> Unit)? = null,
) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    var isPlaying by mutableStateOf(false)
        private set

    var currentParagraphIndex by mutableIntStateOf(0)
        private set

    var speechRate by mutableFloatStateOf(1.0f)
        private set

    private var paragraphs: List<String> = emptyList()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.JAPANESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
                engine.setSpeechRate(speechRate)
                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            isPlaying = true
                        }

                        override fun onDone(utteranceId: String?) {
                            val nextIndex = currentParagraphIndex + 1
                            if (nextIndex < paragraphs.size) {
                                currentParagraphIndex = nextIndex
                                speakParagraph(nextIndex)
                            } else {
                                isPlaying = false
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            isPlaying = false
                        }
                    },
                )
            }
        }
    }

    fun startReading(
        rawText: String,
        startIndex: Int = 0,
    ) {
        val cleaned =
            rawText
                .replace(RUBY_REGEX, "$1")
                .replace(JUMP_REGEX, "")
                .replace(NEWPAGE_REGEX, "\n\n")
                .replace(PIXIV_IMAGE_REGEX, "")
                .replace(CHAPTER_REGEX, "$1")

        paragraphs = cleaned.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return

        currentParagraphIndex = startIndex.coerceIn(0, paragraphs.lastIndex)
        isPlaying = true
        speakParagraph(currentParagraphIndex)
    }

    private fun speakParagraph(index: Int) {
        if (!isInitialized || tts == null || index !in paragraphs.indices) {
            isPlaying = false
            return
        }
        val text = paragraphs[index]
        tts?.setSpeechRate(speechRate)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "paragraph_$index")
    }

    fun pause() {
        tts?.stop()
        isPlaying = false
    }

    fun resume() {
        if (paragraphs.isNotEmpty()) {
            isPlaying = true
            speakParagraph(currentParagraphIndex)
        }
    }

    fun stop() {
        tts?.stop()
        isPlaying = false
        currentParagraphIndex = 0
    }

    fun setRate(rate: Float) {
        speechRate = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        tts?.setSpeechRate(speechRate)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        isPlaying = false
    }

    companion object {
        private const val MIN_SPEECH_RATE = 0.5f
        private const val MAX_SPEECH_RATE = 2.5f
    }
}
