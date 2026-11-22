package com.yunfie.illustia.models

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.yunfie.illustia.R

@Immutable
enum class HomeFeedKind(
    @StringRes val labelResId: Int,
) {
    Recommended(R.string.feed_recommended),
    Ranking(R.string.feed_ranking),
    New(R.string.feed_new),
}

@Immutable
enum class Restrict(
    val apiValue: String,
    @StringRes val labelResId: Int,
) {
    Public("public", R.string.restrict_public),
    Private("private", R.string.restrict_private),
}
