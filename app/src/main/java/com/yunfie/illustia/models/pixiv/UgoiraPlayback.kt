package com.yunfie.illustia.models.pixiv

data class UgoiraPlayback(
    val frames: List<UgoiraPlaybackFrame>,
)

data class UgoiraPlaybackFrame(
    val filePath: String,
    val delayMillis: Int,
)
