package com.yunfie.illustia.ui.screens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Controller and state holder for novel text-to-speech audio playback in Compose UI.
 * Connects Compose screens with [NovelTtsService] (MediaSession / Media Control API).
 */
class NovelTtsPlayer(
    private val context: Context,
    private val onPageAdvance: ((Int) -> Unit)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var isPlaying by mutableStateOf(false)
        private set

    var currentParagraphIndex by mutableIntStateOf(0)
        private set

    var speechRate by mutableFloatStateOf(DEFAULT_SPEECH_RATE)
        private set

    var pitch by mutableFloatStateOf(DEFAULT_PITCH)
        private set

    var voiceName by mutableStateOf("")
        private set

    var availableVoices by mutableStateOf<List<String>>(emptyList())
        private set

    var totalParagraphs by mutableIntStateOf(0)
        private set

    init {
        scope.launch {
            NovelTtsService.ttsState.collect { state ->
                val prevIndex = currentParagraphIndex
                isPlaying = state.isPlaying
                currentParagraphIndex = state.currentParagraphIndex
                speechRate = state.speechRate
                pitch = state.pitch
                voiceName = state.voiceName
                availableVoices = state.availableVoices
                totalParagraphs = state.totalParagraphs

                if (prevIndex != state.currentParagraphIndex) {
                    onPageAdvance?.invoke(state.currentParagraphIndex)
                }
            }
        }
    }

    fun startReading(
        rawText: String,
        startIndex: Int = 0,
        novelTitle: String = "",
        authorName: String = "",
        novelId: Long? = null,
        rate: Float = speechRate,
        pitchLevel: Float = pitch,
        voice: String = voiceName,
        skipSymbols: Boolean = true,
        customDictionary: Map<String, String> = emptyMap(),
    ) {
        NovelTtsService.startReading(
            context = context,
            novelId = novelId,
            novelTitle = novelTitle,
            authorName = authorName,
            rawText = rawText,
            startIndex = startIndex,
            speedRate = rate,
            pitch = pitchLevel,
            voiceName = voice,
            skipSymbols = skipSymbols,
            customDictionary = customDictionary,
        )
    }

    fun pause() {
        NovelTtsService.pause(context)
    }

    fun resume() {
        NovelTtsService.resume(context)
    }

    fun skipToNext() {
        NovelTtsService.skipToNext(context)
    }

    fun skipToPrevious() {
        NovelTtsService.skipToPrevious(context)
    }

    fun seekToParagraph(index: Int) {
        NovelTtsService.seekToParagraph(context, index)
    }

    fun stop() {
        NovelTtsService.stop(context)
    }

    fun setRate(rate: Float) {
        val clamped = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        speechRate = clamped
        NovelTtsService.setSpeed(context, clamped)
    }

    fun setPitchLevel(pitchValue: Float) {
        val clamped = pitchValue.coerceIn(MIN_PITCH, MAX_PITCH)
        pitch = clamped
        NovelTtsService.setPitch(context, clamped)
    }

    fun setVoiceSelection(name: String) {
        voiceName = name
        NovelTtsService.setVoice(context, name)
    }

    fun shutdown() {
        // Cancel the Compose UI scope; service continues playback in background if playing.
        scope.cancel()
    }

    companion object {
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val MIN_SPEECH_RATE = 0.5f
        private const val MAX_SPEECH_RATE = 2.5f

        private const val DEFAULT_PITCH = 1.0f
        private const val MIN_PITCH = 0.5f
        private const val MAX_PITCH = 2.0f
    }
}
