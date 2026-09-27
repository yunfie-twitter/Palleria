package com.yunfie.illustia.models

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.yunfie.illustia.R

@Immutable
enum class SearchAgeRestriction(
    private val keyword: String?,
    @StringRes val labelResId: Int,
) {
    All(null, R.string.search_age_all),
    R18("R-18", R.string.search_age_r18),
    R18G("R-18G", R.string.search_age_r18g),
    ;

    // Use the same tag-query mechanism as the bookmark-count search option.
    // The repository cache and Pixiv next_url retain the resulting query.
    fun applyToQuery(word: String): String = listOfNotNull(word.trim().takeIf { it.isNotEmpty() }, keyword).joinToString(" ")
}
