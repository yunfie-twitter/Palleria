package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable

const val SESSION_EXPIRY_SKEW_MILLIS = 60_000L
const val MAX_SESSION_LIFETIME_SECONDS = 86_400L

@Immutable
@kotlinx.serialization.Serializable
data class PixivSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long?,
    val expiresAtMillis: Long = 0,
)

@Immutable
@kotlinx.serialization.Serializable
data class StoredAccount(
    val name: String,
    val account: String,
    val profileImageUrl: String?,
    val refreshToken: String,
    val userId: Long,
)
