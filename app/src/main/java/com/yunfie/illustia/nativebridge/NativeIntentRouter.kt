package com.yunfie.illustia.nativebridge

import android.content.Intent
import android.net.Uri

sealed interface NativeIntentEvent {
    data class Artwork(val id: Long) : NativeIntentEvent
    data class User(val id: Long) : NativeIntentEvent
    data class Text(val value: String) : NativeIntentEvent
    data class Image(val uri: Uri) : NativeIntentEvent
}

object NativeIntentRouter {
    const val EXTRA_HANDOFF_URI = "com.yunfie.illustia.extra.HANDOFF_URI"

    fun parse(intent: Intent?): NativeIntentEvent? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND) {
            val imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            if (imageUri != null) return NativeIntentEvent.Image(imageUri)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (text.isNotBlank()) {
                parseText(text)?.let { return it }
                return NativeIntentEvent.Text(text)
            }
        }
        if (intent.action == Intent.ACTION_VIEW) {
            parseText(intent.dataString)?.let { return it }
            intent.getStringExtra(EXTRA_HANDOFF_URI)
                ?.takeIf(String::isNotBlank)
                ?.let(::parseText)
                ?.let { return it }
            intent.getLongExtra("iid", 0L).takeIf { it > 0L }?.let {
                return NativeIntentEvent.Artwork(it)
            }
        }
        return null
    }

    fun parseText(value: String?): NativeIntentEvent? {
        return parseUri(value)
    }

    private fun parseUri(value: String?): NativeIntentEvent? {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val segments = uri.pathSegments
        val artworkIndex = segments.indexOfFirst { it == "artworks" || it == "illusts" }
        if (artworkIndex >= 0) {
            segments.getOrNull(artworkIndex + 1)?.toLongOrNull()?.let {
                return NativeIntentEvent.Artwork(it)
            }
        }
        if (uri.host == "illusts") {
            segments.firstOrNull()?.toLongOrNull()?.let { return NativeIntentEvent.Artwork(it) }
        }
        val userIndex = segments.indexOfFirst { it == "users" }
        if (userIndex >= 0) {
            segments.getOrNull(userIndex + 1)?.toLongOrNull()?.let {
                return NativeIntentEvent.User(it)
            }
        }
        if (uri.host == "users") {
            segments.firstOrNull()?.toLongOrNull()?.let { return NativeIntentEvent.User(it) }
        }
        return null
    }
}
