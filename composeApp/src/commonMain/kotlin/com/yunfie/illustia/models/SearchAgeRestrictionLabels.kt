package com.yunfie.illustia.models

import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.search_age_all
import com.yunfie.illustia.resources.search_age_r18
import com.yunfie.illustia.resources.search_age_r18g
import org.jetbrains.compose.resources.StringResource

/** 年齢制限の UI ラベル。 */
val SearchAgeRestriction.labelResId: StringResource
    get() =
        when (this) {
            SearchAgeRestriction.All -> Res.string.search_age_all
            SearchAgeRestriction.R18 -> Res.string.search_age_r18
            SearchAgeRestriction.R18G -> Res.string.search_age_r18g
        }
