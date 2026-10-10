package com.yunfie.illustia.ui.components

private const val CACHE_KEY_EXTRA_CAPACITY = 24

/** Keeps differently decoded versions of the same source image in separate memory entries. */
internal fun pixivImageMemoryCacheKey(
    url: String,
    thumbnail: Boolean,
    decodeDimensionPx: Int?,
): String =
    buildString(url.length + CACHE_KEY_EXTRA_CAPACITY) {
        append(url)
        append("#")
        append(if (thumbnail) "thumbnail" else "image")
        append("-")
        append(decodeDimensionPx ?: "original")
    }
