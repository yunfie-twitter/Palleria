package com.yunfie.illustia.ui.components

/** Keeps differently decoded versions of the same source image in separate memory entries. */
internal fun pixivImageMemoryCacheKey(
    url: String,
    thumbnail: Boolean,
    decodeDimensionPx: Int?,
): String =
    buildString(url.length + 24) {
        append(url)
        append("#")
        append(if (thumbnail) "thumbnail" else "image")
        append("-")
        append(decodeDimensionPx ?: "original")
    }
