package com.yunfie.illustia.models.pixiv

data class UgoiraMetadataResponse(
    val ugoiraMetadata: UgoiraMetadata,
)

data class UgoiraMetadata(
    val zipUrls: UgoiraZipUrls,
    val frames: List<UgoiraFrame> = emptyList(),
)

data class UgoiraFrame(
    val file: String,
    val delay: Int,
)

data class UgoiraZipUrls(
    val medium: String,
)
