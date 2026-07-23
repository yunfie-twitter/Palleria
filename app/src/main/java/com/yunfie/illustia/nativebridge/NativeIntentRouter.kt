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
    private val WEB_PIXIV_HOSTS = setOf("pixiv.net", "www.pixiv.net")
    private val CUSTOM_PIXIV_SCHEMES = setOf("pixiv", "pixez")
    private val CUSTOM_PIXIV_HOSTS = setOf("pixiv.net", "www.pixiv.net", "users", "illusts")

    const val EXTRA_HANDOFF_URI = "com.yunfie.illustia.extra.HANDOFF_URI"
    const val MAX_PROCESS_TEXT_CODE_POINTS = 256

    fun parse(intent: Intent?): NativeIntentEvent? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            return normalizeProcessText(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
                ?.let(NativeIntentEvent::Text)
        }
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

    fun normalizeProcessText(value: CharSequence?): String? {
        if (value == null) return null
        val normalized = buildString(value.length.coerceAtMost(MAX_PROCESS_TEXT_CODE_POINTS)) {
            var pendingSpace = false
            value.forEach { char ->
                when {
                    char.isWhitespace() -> pendingSpace = isNotEmpty()
                    char.isISOControl() -> Unit
                    else -> {
                        if (pendingSpace) append(' ')
                        append(char)
                        pendingSpace = false
                    }
                }
            }
        }.trim()
            .removePrefix("#")
            .trimStart()
        if (normalized.isBlank()) return null

        val codePointCount = normalized.codePointCount(0, normalized.length)
        if (codePointCount <= MAX_PROCESS_TEXT_CODE_POINTS) return normalized
        val endIndex = normalized.offsetByCodePoints(0, MAX_PROCESS_TEXT_CODE_POINTS)
        return normalized.substring(0, endIndex).trimEnd()
    }

    private fun parseUri(value: String?): NativeIntentEvent? {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        if (!uri.isTrustedPixivRoute()) return null
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

    private fun Uri.isTrustedPixivRoute(): Boolean {
        val normalizedScheme = scheme?.lowercase() ?: return false
        val normalizedHost = host?.lowercase() ?: return false
        return when (normalizedScheme) {
            "http", "https" -> normalizedHost in WEB_PIXIV_HOSTS
            in CUSTOM_PIXIV_SCHEMES -> normalizedHost in CUSTOM_PIXIV_HOSTS
            else -> false
        }
    }
}
