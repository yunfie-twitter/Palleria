package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import java.util.Locale

internal fun buildDownloadPath(
    filename: String,
    illust: Illust?,
    groupByArtist: Boolean,
    groupByWork: Boolean,
    customPathTemplate: String? = null,
): String {
    if (!customPathTemplate.isNullOrBlank()) {
        return buildCustomDownloadPath(filename, illust, customPathTemplate)
    }
    val folders =
        buildList {
            if (groupByArtist) {
                illust?.downloadArtistFolder()?.let(::add)
            }
            if (groupByWork) {
                illust?.downloadWorkFolder()?.let(::add)
            }
        }
    return (folders + filename.sanitizeDownloadSegment()).joinToString("/")
}

internal fun buildCustomDownloadPath(
    filename: String,
    illust: Illust?,
    template: String = "{artist}/{tag}/{filename}",
): String {
    if (illust == null) return filename.sanitizeDownloadSegment()
    val artist = illust.artistName.sanitizeOptionalDownloadSegment() ?: "artist_${illust.artistId}"
    val title = illust.title.sanitizeOptionalDownloadSegment() ?: "work_${illust.id}"
    val primaryTag = illust.tags.firstOrNull()?.sanitizeOptionalDownloadSegment() ?: "general"
    val illustId = illust.id.toString()
    val artistId = illust.artistId.toString()
    val type = illust.type.ifBlank { "illust" }

    val baseFilename = filename.substringAfterLast('/').substringBeforeLast('.')
    val ext = filename.substringAfterLast('.', "").takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""

    val resolved = template
        .replace("{artist_name}", artist, ignoreCase = true)
        .replace("{artist}", artist, ignoreCase = true)
        .replace("{artist_id}", artistId, ignoreCase = true)
        .replace("{work_title}", title, ignoreCase = true)
        .replace("{work}", title, ignoreCase = true)
        .replace("{title}", title, ignoreCase = true)
        .replace("{illust_id}", illustId, ignoreCase = true)
        .replace("{id}", illustId, ignoreCase = true)
        .replace("{tag_primary}", primaryTag, ignoreCase = true)
        .replace("{tag}", primaryTag, ignoreCase = true)
        .replace("{type}", type, ignoreCase = true)
        .replace("{filename}", baseFilename, ignoreCase = true)

    val segments = resolved.split('/', '\\').filter { it.isNotBlank() }.map { it.sanitizeDownloadSegment() }
    val joined = segments.joinToString("/")
    return if (joined.endsWith(ext) || ext.isEmpty()) joined else "$joined$ext"
}

internal fun String.withImageExtension(
    sourceUrl: String,
    responseMimeType: String?,
): String {
    if (contains('.')) return this
    val extension =
        when (responseMimeType?.substringBefore(';')?.lowercase(Locale.ROOT)) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> sourceUrl.extractUrlExtension()
        }
    return extension?.takeIf(String::isNotBlank)?.let { "$this.$it" } ?: this
}

private const val MIN_EXTENSION_LENGTH = 2
private const val MAX_EXTENSION_LENGTH = 5

private fun String.extractUrlExtension(): String? =
    substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('.', "")
        .takeIf { it.length in MIN_EXTENSION_LENGTH..MAX_EXTENSION_LENGTH }

internal fun extractIllustId(filename: String): Long? =
    ILLUST_ID_PATTERN
        .find(filename)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()

internal fun Illust.hasImageUrl(url: String): Boolean =
    imageUrl == url ||
        mediumImageUrl == url ||
        originalImageUrl == url ||
        mediumImagePages.any { it == url } ||
        imagePages.any { it == url } ||
        originalImagePages.any { it == url }

private fun Illust.downloadArtistFolder(): String? =
    artistName.sanitizeOptionalDownloadSegment()
        ?: artistId.takeIf { it > 0L }?.let { "artist_$it" }

private fun Illust.downloadWorkFolder(): String? {
    val id = id.takeIf { it > 0L }
    return title
        .sanitizeOptionalDownloadSegment()
        ?.let { title -> id?.let { "${title}_$it" } ?: title }
        ?: id?.let { "work_$it" }
}

private fun String.sanitizeDownloadSegment(maxLength: Int = 80): String = sanitizeOptionalDownloadSegment(maxLength) ?: "untitled"

private fun String.sanitizeOptionalDownloadSegment(maxLength: Int = 80): String? =
    trim()
        .replace(INVALID_PATH_CHARACTER_PATTERN, "_")
        .replace(WHITESPACE_PATTERN, " ")
        .trim(' ', '.')
        .take(maxLength)
        .takeIf(String::isNotBlank)

private val ILLUST_ID_PATTERN = Regex("""(?:^|[^0-9])illustia_(\d+)""")
private val INVALID_PATH_CHARACTER_PATTERN = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
private val WHITESPACE_PATTERN = Regex("""\s+""")
