package com.yunfie.illustia.models.pixiv

data class IllustPersist(
    var id: Long? = null,
    val illustId: Long,
    val userId: Long,
    val pictureUrl: String,
    val userName: String? = null,
    val title: String? = null,
    val time: Long,
)
