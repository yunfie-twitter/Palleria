package com.yunfie.illustia.models.pixiv

data class UgoiraPlayback(
    val frames: List<UgoiraPlaybackFrame>,
)

data class UgoiraPlaybackFrame(
    val filePath: String,
    val delayMillis: Int,
)

private const val MIN_VALID_UGOIRA_DELAY_MILLIS = 20
private const val DEFAULT_UGOIRA_DELAY_MILLIS = 100L

fun normalizedUgoiraDelayMillis(delayMillis: Int): Long =
    if (delayMillis >= MIN_VALID_UGOIRA_DELAY_MILLIS) {
        delayMillis.toLong()
    } else {
        DEFAULT_UGOIRA_DELAY_MILLIS
    }
