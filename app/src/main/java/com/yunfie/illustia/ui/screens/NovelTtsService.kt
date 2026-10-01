package com.yunfie.illustia.ui.screens

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.store.decodeStringMap
import com.yunfie.illustia.settings.store.encodeStringMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * State of the Novel Text-to-Speech audio playback.
 */
data class NovelTtsState(
    val isPlaying: Boolean = false,
    val novelId: Long? = null,
    val novelTitle: String = "",
    val authorName: String = "",
    val currentParagraphIndex: Int = 0,
    val totalParagraphs: Int = 0,
    val currentParagraphText: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String = "",
    val availableVoices: List<String> = emptyList(),
    val isInitialized: Boolean = false,
)

/**
 * Foreground Service for Novel Text-to-Speech playback with Android Media Control API.
 * Integrates with [MediaSession] and [android.app.Notification.MediaStyle] to enable playback control
 * from lock screen, notification shade, Bluetooth headsets, and smartwatches.
 */
@Suppress("TooManyFunctions")
class NovelTtsService :
    Service(),
    TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private var mediaSession: MediaSession? = null
    private var audioHelper: NovelTtsAudioHelper? = null

    private var paragraphs: List<String> = emptyList()
    private var currentParagraphIndex = 0
    private var novelId: Long? = null
    private var novelTitle = ""
    private var authorName = ""
    private var speechRate = DEFAULT_SPEECH_RATE
    private var pitch = DEFAULT_PITCH
    private var voiceName = ""
    private var skipSymbols = true
    private var customDictionary: Map<String, String> = emptyMap()
    private var availableVoices: List<String> = emptyList()

    private val mediaSessionCallback =
        object : MediaSession.Callback() {
            override fun onPlay() {
                resumeReading()
            }

            override fun onPause() {
                pauseReading()
            }

            override fun onSkipToNext() {
                skipToNext()
            }

            override fun onSkipToPrevious() {
                skipToPrevious()
            }

            override fun onStop() {
                stopReading()
            }

            override fun onSeekTo(pos: Long) {
                seekToParagraph(pos.toInt())
            }
        }

    override fun onCreate() {
        super.onCreate()
        NovelTtsNotificationHelper.createNotificationChannel(this)
        setupMediaSession()
        audioHelper =
            NovelTtsAudioHelper(
                context = this,
                onLoss = { pauseReading() },
                onGain = { if (!isPlaying()) resumeReading() },
            )
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
            tts?.let { engine ->
                val result = engine.setLanguage(Locale.JAPANESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
                engine.setSpeechRate(speechRate)
                engine.setPitch(pitch)

                // ボイス一覧の取得
                availableVoices =
                    runCatching {
                        engine.voices
                            ?.filter { it.locale.language == Locale.JAPANESE.language || it.locale == Locale.getDefault() }
                            ?.map { it.name }
                            ?.sorted()
                            .orEmpty()
                    }.getOrDefault(emptyList())

                applyVoice(engine)

                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            updateState(isPlaying = true)
                            updatePlaybackState(PlaybackState.STATE_PLAYING)
                            updateNotification()
                        }

                        override fun onDone(utteranceId: String?) {
                            val nextIndex = currentParagraphIndex + 1
                            if (nextIndex < paragraphs.size) {
                                currentParagraphIndex = nextIndex
                                speakCurrentParagraph()
                            } else {
                                updateState(isPlaying = false)
                                updatePlaybackState(PlaybackState.STATE_STOPPED)
                                updateNotification()
                                audioHelper?.abandonAudioFocus()
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            updateState(isPlaying = false)
                            updatePlaybackState(PlaybackState.STATE_PAUSED)
                            updateNotification()
                        }
                    },
                )
            }
            updateState(isInitialized = true)
            if (paragraphs.isNotEmpty() && _ttsState.value.isPlaying) {
                speakCurrentParagraph()
            }
        }
    }

    private fun applyVoice(engine: TextToSpeech? = tts) {
        if (voiceName.isNotBlank() && engine != null) {
            runCatching {
                val voice = engine.voices?.firstOrNull { it.name == voiceName }
                if (voice != null) {
                    engine.voice = voice
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START_READING -> {
                novelId = intent.getLongExtra(EXTRA_NOVEL_ID, -1L).takeIf { it != -1L }
                novelTitle = intent.getStringExtra(EXTRA_NOVEL_TITLE).orEmpty()
                authorName = intent.getStringExtra(EXTRA_AUTHOR_NAME).orEmpty()
                speechRate = intent.getFloatExtra(EXTRA_SPEED_RATE, DEFAULT_SPEECH_RATE)
                pitch = intent.getFloatExtra(EXTRA_PITCH, DEFAULT_PITCH)
                voiceName = intent.getStringExtra(EXTRA_VOICE_NAME).orEmpty()
                skipSymbols = intent.getBooleanExtra(EXTRA_SKIP_SYMBOLS, true)
                customDictionary = decodeStringMap(intent.getStringExtra(EXTRA_CUSTOM_DICTIONARY_JSON))
                val rawText = intent.getStringExtra(EXTRA_RAW_TEXT).orEmpty()
                val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
                handleStartReading(rawText, startIndex)
            }

            ACTION_PLAY -> {
                resumeReading()
            }

            ACTION_PAUSE -> {
                pauseReading()
            }

            ACTION_TOGGLE_PLAY -> {
                if (isPlaying()) pauseReading() else resumeReading()
            }

            ACTION_NEXT -> {
                skipToNext()
            }

            ACTION_PREV -> {
                skipToPrevious()
            }

            ACTION_STOP -> {
                stopReading()
            }

            ACTION_SET_SPEED -> {
                setSpeedRate(intent.getFloatExtra(EXTRA_SPEED_RATE, speechRate))
            }

            ACTION_SET_PITCH -> {
                setPitchLevel(intent.getFloatExtra(EXTRA_PITCH, pitch))
            }

            ACTION_SET_VOICE -> {
                setVoiceByName(intent.getStringExtra(EXTRA_VOICE_NAME).orEmpty())
            }

            ACTION_SEEK_PARAGRAPH -> {
                seekToParagraph(intent.getIntExtra(EXTRA_PARAGRAPH_INDEX, currentParagraphIndex))
            }
        }
        return START_NOT_STICKY
    }

    private fun handleStartReading(
        rawText: String,
        startIndex: Int,
    ) {
        paragraphs = NovelTtsTextSanitizer.sanitizeTextToParagraphs(rawText, skipSymbols, customDictionary)
        if (paragraphs.isEmpty()) return

        currentParagraphIndex = startIndex.coerceIn(0, paragraphs.lastIndex)
        audioHelper?.requestAudioFocus()
        startForeground(
            NovelTtsNotificationHelper.NOTIFICATION_ID,
            buildNotification(),
        )
        updateMediaMetadata()
        updatePlaybackState(PlaybackState.STATE_PLAYING)

        if (isTtsInitialized) {
            applyVoice()
            speakCurrentParagraph()
        } else {
            updateState(isPlaying = true)
        }
    }

    private fun speakCurrentParagraph() {
        if (!isTtsInitialized || tts == null || currentParagraphIndex !in paragraphs.indices) {
            updateState(isPlaying = false)
            return
        }
        val text = paragraphs[currentParagraphIndex]
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "paragraph_$currentParagraphIndex")
        updateState(isPlaying = true)
        updatePlaybackState(PlaybackState.STATE_PLAYING)
        updateNotification()
    }

    private fun resumeReading() {
        if (paragraphs.isEmpty()) return
        audioHelper?.requestAudioFocus()
        startForeground(
            NovelTtsNotificationHelper.NOTIFICATION_ID,
            buildNotification(),
        )
        speakCurrentParagraph()
    }

    private fun pauseReading() {
        tts?.stop()
        updateState(isPlaying = false)
        updatePlaybackState(PlaybackState.STATE_PAUSED)
        updateNotification()
    }

    private fun skipToNext() {
        if (currentParagraphIndex < paragraphs.size - 1) {
            currentParagraphIndex++
            speakCurrentParagraph()
        }
    }

    private fun skipToPrevious() {
        if (currentParagraphIndex > 0) {
            currentParagraphIndex--
            speakCurrentParagraph()
        }
    }

    private fun seekToParagraph(index: Int) {
        if (paragraphs.isNotEmpty()) {
            currentParagraphIndex = index.coerceIn(0, paragraphs.lastIndex)
            speakCurrentParagraph()
        }
    }

    private fun setSpeedRate(rate: Float) {
        speechRate = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        tts?.setSpeechRate(speechRate)
        updateState(isPlaying = isPlaying())
    }

    private fun setPitchLevel(pitchValue: Float) {
        pitch = pitchValue.coerceIn(MIN_PITCH, MAX_PITCH)
        tts?.setPitch(pitch)
        updateState(isPlaying = isPlaying())
    }

    private fun setVoiceByName(name: String) {
        voiceName = name
        applyVoice()
        updateState(isPlaying = isPlaying())
    }

    private fun stopReading() {
        tts?.stop()
        audioHelper?.abandonAudioFocus()
        updateState(isPlaying = false, currentParagraphIndex = 0)
        updatePlaybackState(PlaybackState.STATE_STOPPED)
        mediaSession?.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun isPlaying(): Boolean = _ttsState.value.isPlaying

    private fun updateState(
        isPlaying: Boolean = _ttsState.value.isPlaying,
        currentParagraphIndex: Int = this.currentParagraphIndex,
        isInitialized: Boolean = this.isTtsInitialized,
    ) {
        val currentText = paragraphs.getOrNull(currentParagraphIndex).orEmpty()
        _ttsState.value =
            NovelTtsState(
                isPlaying = isPlaying,
                novelId = novelId,
                novelTitle = novelTitle,
                authorName = authorName,
                currentParagraphIndex = currentParagraphIndex,
                totalParagraphs = paragraphs.size,
                currentParagraphText = currentText,
                speechRate = speechRate,
                pitch = pitch,
                voiceName = voiceName,
                availableVoices = availableVoices,
                isInitialized = isInitialized,
            )
    }

    private fun setupMediaSession() {
        mediaSession =
            MediaSession(this, "NovelTtsMediaSession").apply {
                setCallback(mediaSessionCallback)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    @Suppress("DEPRECATION")
                    setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                }
                isActive = true
            }
    }

    private fun updatePlaybackState(state: Int) {
        val session = mediaSession ?: return
        val actions =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_STOP
        val playbackState =
            PlaybackState
                .Builder()
                .setActions(actions)
                .setState(state, currentParagraphIndex.toLong(), 1.0f)
                .build()
        session.setPlaybackState(playbackState)
    }

    private fun updateMediaMetadata() {
        val session = mediaSession ?: return
        val metadata =
            MediaMetadata
                .Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, novelTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, authorName)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, getString(R.string.app_name))
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, paragraphs.size.toLong())
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, (currentParagraphIndex + 1).toLong())
                .build()
        session.setMetadata(metadata)
    }

    private fun buildNotification() =
        NovelTtsNotificationHelper.buildNotification(
            service = this,
            novelTitle = novelTitle,
            authorName = authorName,
            currentParagraphIndex = currentParagraphIndex,
            totalParagraphs = paragraphs.size,
            isPlaying = isPlaying(),
            mediaSession = mediaSession,
        )

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(
            NovelTtsNotificationHelper.NOTIFICATION_ID,
            buildNotification(),
        )
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        mediaSession?.release()
        mediaSession = null
        audioHelper?.abandonAudioFocus()
        updateState(isPlaying = false, currentParagraphIndex = 0, isInitialized = false)
        super.onDestroy()
    }

    companion object {
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val MIN_SPEECH_RATE = 0.5f
        private const val MAX_SPEECH_RATE = 2.5f

        private const val DEFAULT_PITCH = 1.0f
        private const val MIN_PITCH = 0.5f
        private const val MAX_PITCH = 2.0f

        const val ACTION_START_READING = "com.yunfie.illustia.tts.START_READING"
        const val ACTION_PLAY = "com.yunfie.illustia.tts.PLAY"
        const val ACTION_PAUSE = "com.yunfie.illustia.tts.PAUSE"
        const val ACTION_TOGGLE_PLAY = "com.yunfie.illustia.tts.TOGGLE_PLAY"
        const val ACTION_NEXT = "com.yunfie.illustia.tts.NEXT"
        const val ACTION_PREV = "com.yunfie.illustia.tts.PREV"
        const val ACTION_STOP = "com.yunfie.illustia.tts.STOP"
        const val ACTION_SET_SPEED = "com.yunfie.illustia.tts.SET_SPEED"
        const val ACTION_SET_PITCH = "com.yunfie.illustia.tts.SET_PITCH"
        const val ACTION_SET_VOICE = "com.yunfie.illustia.tts.SET_VOICE"
        const val ACTION_SEEK_PARAGRAPH = "com.yunfie.illustia.tts.SEEK_PARAGRAPH"

        const val EXTRA_NOVEL_ID = "novel_id"
        const val EXTRA_NOVEL_TITLE = "novel_title"
        const val EXTRA_AUTHOR_NAME = "author_name"
        const val EXTRA_RAW_TEXT = "raw_text"
        const val EXTRA_START_INDEX = "start_index"
        const val EXTRA_SPEED_RATE = "speed_rate"
        const val EXTRA_PITCH = "pitch"
        const val EXTRA_VOICE_NAME = "voice_name"
        const val EXTRA_SKIP_SYMBOLS = "skip_symbols"
        const val EXTRA_CUSTOM_DICTIONARY_JSON = "custom_dictionary_json"
        const val EXTRA_PARAGRAPH_INDEX = "paragraph_index"

        private val _ttsState = MutableStateFlow(NovelTtsState())
        val ttsState: StateFlow<NovelTtsState> = _ttsState.asStateFlow()

        fun startReading(
            context: Context,
            novelId: Long? = null,
            novelTitle: String = "",
            authorName: String = "",
            rawText: String,
            startIndex: Int = 0,
            speedRate: Float = DEFAULT_SPEECH_RATE,
            pitch: Float = DEFAULT_PITCH,
            voiceName: String = "",
            skipSymbols: Boolean = true,
            customDictionary: Map<String, String> = emptyMap(),
        ) {
            val intent =
                Intent(context, NovelTtsService::class.java).apply {
                    action = ACTION_START_READING
                    putExtra(EXTRA_NOVEL_ID, novelId ?: -1L)
                    putExtra(EXTRA_NOVEL_TITLE, novelTitle)
                    putExtra(EXTRA_AUTHOR_NAME, authorName)
                    putExtra(EXTRA_RAW_TEXT, rawText)
                    putExtra(EXTRA_START_INDEX, startIndex)
                    putExtra(EXTRA_SPEED_RATE, speedRate)
                    putExtra(EXTRA_PITCH, pitch)
                    putExtra(EXTRA_VOICE_NAME, voiceName)
                    putExtra(EXTRA_SKIP_SYMBOLS, skipSymbols)
                    putExtra(EXTRA_CUSTOM_DICTIONARY_JSON, encodeStringMap(customDictionary))
                }
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, NovelTtsService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, NovelTtsService::class.java).apply { action = ACTION_PLAY }
            ContextCompat.startForegroundService(context, intent)
        }

        fun togglePlay(context: Context) {
            val intent = Intent(context, NovelTtsService::class.java).apply { action = ACTION_TOGGLE_PLAY }
            ContextCompat.startForegroundService(context, intent)
        }

        fun skipToNext(context: Context) {
            val intent = Intent(context, NovelTtsService::class.java).apply { action = ACTION_NEXT }
            context.startService(intent)
        }

        fun skipToPrevious(context: Context) {
            val intent = Intent(context, NovelTtsService::class.java).apply { action = ACTION_PREV }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, NovelTtsService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }

        fun setSpeed(
            context: Context,
            speedRate: Float,
        ) {
            val intent =
                Intent(context, NovelTtsService::class.java).apply {
                    action = ACTION_SET_SPEED
                    putExtra(EXTRA_SPEED_RATE, speedRate)
                }
            context.startService(intent)
        }

        fun setPitch(
            context: Context,
            pitch: Float,
        ) {
            val intent =
                Intent(context, NovelTtsService::class.java).apply {
                    action = ACTION_SET_PITCH
                    putExtra(EXTRA_PITCH, pitch)
                }
            context.startService(intent)
        }

        fun setVoice(
            context: Context,
            voiceName: String,
        ) {
            val intent =
                Intent(context, NovelTtsService::class.java).apply {
                    action = ACTION_SET_VOICE
                    putExtra(EXTRA_VOICE_NAME, voiceName)
                }
            context.startService(intent)
        }

        fun seekToParagraph(
            context: Context,
            index: Int,
        ) {
            val intent =
                Intent(context, NovelTtsService::class.java).apply {
                    action = ACTION_SEEK_PARAGRAPH
                    putExtra(EXTRA_PARAGRAPH_INDEX, index)
                }
            context.startService(intent)
        }
    }
}
