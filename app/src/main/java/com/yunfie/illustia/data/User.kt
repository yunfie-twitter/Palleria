package com.yunfie.illustia.data

import androidx.compose.runtime.Immutable

@Immutable
data class UserPreview(
    val id: Long,
    val name: String,
    val account: String,
    val profileImageUrl: String?,
    val comment: String,
    val isFollowed: Boolean,
    val previewIllusts: List<Illust>,
)

@Immutable
data class UserProfile(
    val id: Long,
    val name: String,
    val account: String,
    val profileImageUrl: String?,
    val backgroundImageUrl: String?,
    val comment: String,
    val isFollowed: Boolean,
)
