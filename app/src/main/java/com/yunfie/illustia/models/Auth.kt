package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable

@Immutable
data class PixivSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long?,
)

@Immutable
data class StoredAccount(
    val name: String,
    val account: String,
    val profileImageUrl: String?,
    val refreshToken: String,
    val userId: Long,
)
