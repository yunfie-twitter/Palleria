package com.yunfie.illustia.nativebridge

import com.yunfie.illustia.platform.IncomingLink

/**
 * Pixiv / Palleria のリンクを解釈して [NativeIntentEvent] へ変換する。
 *
 * `Intent` の読み取りは `androidApp` 側に残し、ここは文字列処理だけを行う。
 */
object NativeIntentRouter {
    private val WEB_PIXIV_HOSTS = setOf("pixiv.net", "www.pixiv.net")
    private val CUSTOM_PIXIV_SCHEMES = setOf("pixiv", "palleria")
    private val CUSTOM_PIXIV_HOSTS = setOf("pixiv.net", "www.pixiv.net", "users", "illusts", "tags")
    private val ROUTE_CANDIDATE_PATTERN = Regex("""(?i)\b(?:https?://|pixiv://|palleria://)\S+""")
    private val URI_PATTERN = Regex("""^(?i)(https?|pixiv|palleria)://([^/?#]+)(?:/(.*))?$""")
    private val TRACKING_PARAMS =
        setOf(
            "s",
            "t",
            "ref",
            "ref_source",
            "utm_source",
            "utm_medium",
            "utm_campaign",
            "utm_term",
            "utm_content",
            "fbclid",
            "gclid",
            "igshid",
            "msclkid",
        )
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

    /** Android 非依存の [IncomingLink] からイベントを組み立てる。ViewModel 層はこちらを使う。 */
    fun parse(link: IncomingLink?): NativeIntentEvent? {
        if (link == null) return null
        if (link.action == IncomingLink.Action.PROCESS_TEXT) {
            return normalizeProcessText(link.processText)?.let(NativeIntentEvent::Text)
        }
        if (link.action == IncomingLink.Action.SEND) {
            link.sharedImageUri?.let { return NativeIntentEvent.Image(it) }
            val text = link.sharedText?.trim().orEmpty()
            if (text.isNotBlank()) {
                parseText(text)?.let { return it }
                return NativeIntentEvent.Text(text)
            }
        }
        if (link.action == IncomingLink.Action.VIEW) {
            parseText(link.dataString)?.let { return it }
            link.handoffUri
                ?.takeIf(String::isNotBlank)
                ?.let(::parseText)
                ?.let { return it }
            link.illustId.takeIf { it > 0L }?.let { return NativeIntentEvent.Artwork(it) }
        }
        return null
    }

    fun cleanTrackingParameters(url: String): String {
        if (!url.contains('?')) return url
        val base = url.substringBefore('?')
        val query = url.substringAfter('?').substringBefore('#')
        val fragment = if (url.contains('#')) "#" + url.substringAfter('#') else ""
        val cleanedQuery =
            query
                .split('&')
                .filter { param ->
                    val key = param.substringBefore('=').lowercase().trim()
                    key.isNotBlank() && key !in TRACKING_PARAMS && !key.startsWith("utm_")
                }.joinToString("&")
        return if (cleanedQuery.isBlank()) {
            base + fragment
        } else {
            "$base?$cleanedQuery$fragment"
        }
    }

    fun sanitizeSearchInput(
        text: String,
        enabled: Boolean = true,
    ): String {
        if (!enabled || text.isBlank()) return text
        // This runs for every keystroke. Keep boundary whitespace so an IME can
        // insert the separator before the next word; parseText trims only for
        // intent detection and does not mutate the user's query.
        val cleaned = cleanTrackingParameters(text)
        // Pixiv の作品URLまたはユーザーURLが直接貼られた場合、ID を抽出して検索欄をすっきりさせる
        val event = parseText(cleaned)
        return when (event) {
            is NativeIntentEvent.Artwork -> event.id.toString()
            is NativeIntentEvent.User -> event.id.toString()
            else -> cleaned
        }
    }

    fun parseText(value: String?): NativeIntentEvent? {
        val normalized = value?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        val cleaned = cleanTrackingParameters(normalized)
        parseUri(cleaned)?.let { return it }
        return ROUTE_CANDIDATE_PATTERN
            .findAll(cleaned)
            .mapNotNull { match -> parseUri(cleanTrackingParameters(match.value.trimEnd(*ROUTE_TRAILING_PUNCTUATION))) }
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
        val match = URI_PATTERN.find(value.trim()) ?: return null
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
            rawTag?.let { runCatching { decodeUrlComponent(it) }.getOrDefault(it) }?.trim()
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
