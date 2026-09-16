package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import java.util.Locale

internal fun buildDownloadPath(
    filename: String,
    illust: Illust?,
    groupByArtist: Boolean,
    groupByWork: Boolean,
): String {
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
