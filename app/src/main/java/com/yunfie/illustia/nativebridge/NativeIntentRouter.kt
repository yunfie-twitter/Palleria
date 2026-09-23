package com.yunfie.illustia.nativebridge

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.net.URI

sealed interface NativeIntentEvent {
    data class Artwork(
        val id: Long,
    ) : NativeIntentEvent

    data class User(
        val id: Long,
    ) : NativeIntentEvent

    data class Tag(
        val tag: String,
    ) : NativeIntentEvent

    data class Text(
        val value: String,
    ) : NativeIntentEvent

    data class Image(
        val uri: Uri,
    ) : NativeIntentEvent
}

object NativeIntentRouter {
    private val WEB_PIXIV_HOSTS = setOf("pixiv.net", "www.pixiv.net")
    private val CUSTOM_PIXIV_SCHEMES = setOf("pixiv", "palleria")
    private val CUSTOM_PIXIV_HOSTS = setOf("pixiv.net", "www.pixiv.net", "users", "illusts", "tags")
    private val ROUTE_CANDIDATE_PATTERN = Regex("""(?i)\b(?:https?://|pixiv://|palleria://)\S+""")
    private val ROUTE_TRAILING_PUNCTUATION =
        charArrayOf(
            '.',
            ',',
            ';',
            ':',
            '!',
            '?',
            ')',
            ']',
            '}',
            '。',
            '、',
            '！',
            '？',
            '）',
            '】',
            '』',
            '」',
        )

    const val EXTRA_HANDOFF_URI = "com.yunfie.illustia.extra.HANDOFF_URI"
    const val MAX_PROCESS_TEXT_CODE_POINTS = 256

    fun parse(intent: Intent?): NativeIntentEvent? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            return normalizeProcessText(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
                ?.let(NativeIntentEvent::Text)
        }
        if (intent.action == Intent.ACTION_SEND) {
            val imageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (imageUri != null) return NativeIntentEvent.Image(imageUri)
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (text.isNotBlank()) {
                parseText(text)?.let { return it }
                return NativeIntentEvent.Text(text)
            }
        }
        if (intent.action == Intent.ACTION_VIEW) {
            parseText(intent.dataString)?.let { return it }
            intent
                .getStringExtra(EXTRA_HANDOFF_URI)
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
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        parseUri(normalized)?.let { return it }
        return ROUTE_CANDIDATE_PATTERN
            .findAll(normalized)
            .mapNotNull { match -> parseUri(match.value.trimEnd(*ROUTE_TRAILING_PUNCTUATION)) }
            .firstOrNull()
    }

    fun normalizeProcessText(value: CharSequence?): String? {
        if (value == null) return null
        val normalized =
            buildString(value.length.coerceAtMost(MAX_PROCESS_TEXT_CODE_POINTS)) {
                var pendingSpace = false
                value.forEach { char ->
                    when {
                        char.isWhitespace() -> {
                            pendingSpace = isNotEmpty()
                        }

                        char.isISOControl() -> {
                            Unit
                        }

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
        if (value.isNullOrBlank()) return null
        val match = Regex("""^(?i)(https?|pixiv|palleria)://([^/?#]+)(?:/(.*))?$""").find(value.trim()) ?: return null
        val normalizedScheme = match.groupValues[1].lowercase()
        val normalizedHost = match.groupValues[2].lowercase()
        if (!isTrustedPixivRoute(normalizedScheme, normalizedHost)) return null
        val rawPath = match.groupValues[3]
        val segments =
            rawPath
                .split('?')[0]
                .split('#')[0]
                .split('/')
                .filter(String::isNotEmpty)
        return parseArtworkEvent(normalizedHost, segments)
            ?: parseUserEvent(normalizedHost, segments)
            ?: parseTagEvent(normalizedHost, segments)
    }

    private fun parseArtworkEvent(
        normalizedHost: String,
        segments: List<String>,
    ): NativeIntentEvent.Artwork? {
        val artworkIndex = segments.indexOfFirst { it == "artworks" || it == "illusts" }
        val id =
            if (artworkIndex >= 0) {
                segments.getOrNull(artworkIndex + 1)?.toLongOrNull()
            } else if (normalizedHost == "illusts") {
                segments.firstOrNull()?.toLongOrNull()
            } else {
                null
            }
        return id?.let(NativeIntentEvent::Artwork)
    }

    private fun parseUserEvent(
        normalizedHost: String,
        segments: List<String>,
    ): NativeIntentEvent.User? {
        val userIndex = segments.indexOfFirst { it == "users" }
        val id =
            if (userIndex >= 0) {
                segments.getOrNull(userIndex + 1)?.toLongOrNull()
            } else if (normalizedHost == "users") {
                segments.firstOrNull()?.toLongOrNull()
            } else {
                null
            }
        return id?.let(NativeIntentEvent::User)
    }

    private fun parseTagEvent(
        normalizedHost: String,
        segments: List<String>,
    ): NativeIntentEvent.Tag? {
        val tagIndex = segments.indexOfFirst { it == "tags" }
        val rawTag =
            if (tagIndex >= 0) {
                segments.getOrNull(tagIndex + 1)
            } else if (normalizedHost == "tags") {
                segments.firstOrNull()
            } else {
                null
            }
        val decodedTag =
            rawTag?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }?.trim()
        return decodedTag?.takeIf(String::isNotBlank)?.let(NativeIntentEvent::Tag)
    }

    private fun isTrustedPixivRoute(
        normalizedScheme: String,
        normalizedHost: String,
    ): Boolean =
        when (normalizedScheme) {
            "http", "https" -> normalizedHost in WEB_PIXIV_HOSTS
            in CUSTOM_PIXIV_SCHEMES -> normalizedHost in CUSTOM_PIXIV_HOSTS
            else -> false
        }
}
