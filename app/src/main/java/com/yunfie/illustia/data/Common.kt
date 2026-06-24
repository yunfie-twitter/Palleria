package com.yunfie.illustia.data

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.yunfie.illustia.R

@Immutable
enum class HomeFeedKind(@StringRes val labelResId: Int) {
    Recommended(R.string.feed_recommended),
    Ranking(R.string.feed_ranking),
    New(R.string.feed_new),
}

@Immutable
enum class Restrict(val apiValue: String, @StringRes val labelResId: Int) {
    Public("public", R.string.restrict_public),
    Private("private", R.string.restrict_private),
}

@Immutable
data class PageResult<T>(
    val items: List<T>,
    val nextUrl: String?,
)

@Immutable
sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data object Loaded : LoadState
    data class Error(val message: String) : LoadState
}
