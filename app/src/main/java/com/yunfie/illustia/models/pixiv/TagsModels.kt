package com.yunfie.illustia.models.pixiv

data class AutoWords(
    val tags: List<PixivTag> = emptyList(),
)

data class TagPersist(
    var id: Long? = null,
    val name: String,
    val translatedName: String,
    var type: Int = 0,
)
