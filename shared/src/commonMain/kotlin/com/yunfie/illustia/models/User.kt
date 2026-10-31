package com.yunfie.illustia.models

data class UserProfile(
    val id: Long,
    val name: String,
    val account: String,
    val profileImageUrl: String?,
    val backgroundImageUrl: String?,
    val comment: String,
    val isFollowed: Boolean,
)
