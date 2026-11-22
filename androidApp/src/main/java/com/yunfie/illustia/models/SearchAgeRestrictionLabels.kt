package com.yunfie.illustia.models

import androidx.annotation.StringRes
import com.yunfie.illustia.R

/** 年齢制限の UI ラベル。 */
@get:StringRes
val SearchAgeRestriction.labelResId: Int
    get() =
        when (this) {
            SearchAgeRestriction.All -> R.string.search_age_all
            SearchAgeRestriction.R18 -> R.string.search_age_r18
            SearchAgeRestriction.R18G -> R.string.search_age_r18g
        }
