package com.yunfie.illustia.models.pixiv

data class KeyValuePair(
    val key: String,
    val value: String,
    val expireTime: Long,
    val dateTime: Long,
)
