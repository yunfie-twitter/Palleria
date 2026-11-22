package com.yunfie.illustia.ui.screens

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.speech.tts.TextToSpeech
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, shadows = [NovelTtsServiceResumeTest.RecordingTts::class])
class NovelTtsServiceResumeTest {
    private lateinit var controller: ServiceController<NovelTtsService>
    private lateinit var service: NovelTtsService
    private lateinit var engine: RecordingTts

    @Before
    fun setUp() {
        controller = Robolectric.buildService(NovelTtsService::class.java).create()
        service = controller.get()
        engine = Shadow.extract(ShadowTextToSpeech.getLastTextToSpeechInstance())
        service.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
        service.onStartCommand(
            Intent(NovelTtsService.ACTION_START_READING)
                .putExtra(NovelTtsService.EXTRA_NOVEL_ID, 42L)
                .putExtra(NovelTtsService.EXTRA_RAW_TEXT, "最初の文。続きの文。最後の文。\n\n次の段落。"),
            0,
            1,
        )
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    @Test
    fun firstParagraphResumesRemainingTextAndRejectsLateCompletion() {
        val oldId = engine.spokenId
        engine.utteranceProgressListener.onRangeStart(oldId, 5, 10, 0)
        shadowOf(Looper.getMainLooper()).idle()
        command(NovelTtsService.ACTION_PAUSE)
        engine.utteranceProgressListener.onDone(oldId)
        engine.utteranceProgressListener.onStart(oldId)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(NovelTtsService.ttsState.value.isPlaying)
        assertEquals(0, NovelTtsService.ttsState.value.currentParagraphIndex)
        command(NovelTtsService.ACTION_PLAY)
        assertEquals("続きの文。最後の文。", engine.spokenText)
        engine.utteranceProgressListener.onRangeStart(engine.spokenId, 5, 10, 0)
        shadowOf(Looper.getMainLooper()).idle()
        command(NovelTtsService.ACTION_PAUSE)
        command(NovelTtsService.ACTION_PLAY)
        assertEquals("最後の文。", engine.spokenText)
    }

    @Test
    fun skippingAfterPauseStartsTheNextParagraphAtItsBeginning() {
        engine.utteranceProgressListener.onRangeStart(engine.spokenId, 5, 10, 0)
        shadowOf(Looper.getMainLooper()).idle()
        command(NovelTtsService.ACTION_PAUSE)
        command(NovelTtsService.ACTION_NEXT)
        assertEquals("次の段落。", engine.spokenText)
        assertEquals(1, NovelTtsService.ttsState.value.currentParagraphIndex)
    }

    @Test
    fun fullStopClearsTheResumableNovel() {
        command(NovelTtsService.ACTION_STOP)
        assertNull(NovelTtsService.ttsState.value.novelId)
        assertEquals(0, NovelTtsService.ttsState.value.totalParagraphs)
    }

    private fun command(action: String) {
        service.onStartCommand(Intent(action), 0, 1)
    }

    @Implements(TextToSpeech::class)
    class RecordingTts : ShadowTextToSpeech() {
        var spokenText = ""
        var spokenId = ""

        @Implementation
        override fun speak(
            text: CharSequence,
            queueMode: Int,
            params: Bundle?,
            utteranceId: String,
        ): Int {
            spokenText = text.toString()
            spokenId = utteranceId
            return TextToSpeech.SUCCESS
        }
    }
}
