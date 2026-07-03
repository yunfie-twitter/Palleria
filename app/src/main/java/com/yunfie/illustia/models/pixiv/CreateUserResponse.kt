package com.yunfie.illustia.models.pixiv

data class CreateUserResponse(
    val error: Boolean,
    val message: String,
    val body: CreateUserBody,
)

data class CreateUserBody(
    val userAccount: String,
    val password: String,
    val deviceToken: String,
)
