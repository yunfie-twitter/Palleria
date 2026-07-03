package com.yunfie.illustia.models.pixiv

data class Account(
    val response: AccountResponse,
)

data class AccountResponse(
    val accessToken: String,
    val expiresIn: Int,
    val tokenType: String,
    val scope: String,
    val refreshToken: String,
    val user: AccountUser,
)

data class AccountPersist(
    var id: Long? = null,
    val userId: String,
    val userImage: String,
    val accessToken: String,
    val refreshToken: String,
    val deviceToken: String,
    val name: String,
    val account: String,
    val mailAddress: String,
    val passWord: String,
    val isPremium: Int,
    val xRestrict: Int,
    val isMailAuthorized: Int,
)

data class AccountUser(
    val profileImageUrls: AccountProfileImageUrls,
    val id: String,
    val name: String,
    val account: String,
    val mailAddress: String,
    val isPremium: Boolean,
    val xRestrict: Int,
    val isMailAuthorized: Boolean,
    val requirePolicyAgreement: Boolean? = null,
)

data class AccountProfileImageUrls(
    val px16x16: String,
    val px50x50: String,
    val px170x170: String,
)
