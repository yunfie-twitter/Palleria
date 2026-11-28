package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable

@Immutable
@kotlinx.serialization.Serializable
enum class SearchAgeRestriction(
    internal val keyword: String?,
) {
    All(null),
    R18("R-18"),
    R18G("R-18G"),
    ;

    // Use the same tag-query mechanism as the bookmark-count search option.
    // The repository cache and Pixiv next_url retain the resulting query.
    fun applyToQuery(word: String): String = listOfNotNull(word.trim().takeIf { it.isNotEmpty() }, keyword).joinToString(" ")
}
