package com.yunfie.illustia.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

internal class NovelTtsAudioHelper(
    context: Context,
    private val onLoss: () -> Unit,
    private val onGain: () -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    var hasAudioFocus = false
        private set

    private val audioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> onLoss()

                AudioManager.AUDIOFOCUS_GAIN -> onGain()
            }
        }

    fun requestAudioFocus(): Boolean {
        if (!hasAudioFocus) {
            val manager = audioManager
            if (manager != null) {
                val result =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val playbackAttributes =
                            AudioAttributes
                                .Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        val request =
                            AudioFocusRequest
                                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                                .setAudioAttributes(playbackAttributes)
                                .setAcceptsDelayedFocusGain(true)
                                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                                .build()
                        audioFocusRequest = request
                        manager.requestAudioFocus(request)
                    } else {
                        @Suppress("DEPRECATION")
                        manager.requestAudioFocus(
                            audioFocusChangeListener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN,
                        )
                    }
                hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        }
        return hasAudioFocus
    }

    fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (!hasAudioFocus) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { manager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }
}
