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
enum class SearchSort(val apiValue: String, @StringRes val labelResId: Int) {
    DateDesc("date_desc", R.string.sort_date_desc),
    DateAsc("date_asc", R.string.sort_date_asc),
    PopularDesc("popular_desc", R.string.sort_popular_desc),
}

@Immutable
enum class SearchTarget(val apiValue: String, @StringRes val labelResId: Int) {
    PartialTags("partial_match_for_tags", R.string.search_target_tags),
    ExactTags("exact_match_for_tags", R.string.search_target_exact),
    TitleAndCaption("title_and_caption", R.string.search_target_title),
}

@Immutable
enum class SearchDuration(val apiValue: String?, @StringRes val labelResId: Int) {
    All(null, R.string.duration_all),
    Day("within_last_day", R.string.duration_24h),
    Week("within_last_week", R.string.duration_1week),
    Month("within_last_month", R.string.duration_1month),
}

@Immutable
enum class SearchBookmarkFilter(val keyword: String?, @StringRes val labelResId: Int) {
    None(null, R.string.bookmark_filter_none),
    Over100("100users入り", R.string.bookmark_filter_100),
    Over500("500users入り", R.string.bookmark_filter_500),
    Over1000("1000users入り", R.string.bookmark_filter_1000),
    Over5000("5000users入り", R.string.bookmark_filter_5000),
}

@Immutable
data class PixivSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long?,
)

@Immutable
data class Illust(
    val id: Long,
    val title: String,
    val type: String,
    val caption: String,
    val artistId: Long,
    val artistName: String,
    val artistAvatarUrl: String?,
    val squareImageUrl: String,
    val mediumImageUrl: String = "",
    val imageUrl: String,
    val originalImageUrl: String?,
    val mediumImagePages: List<String> = emptyList(),
    val imagePages: List<String> = emptyList(),
    val originalImagePages: List<String> = emptyList(),
    val tags: List<String>,
    val pageCount: Int,
    val isBookmarked: Boolean,
) {
    /** サムネイル用URL (square → medium → original のフォールバック) */
    val thumbnailUrl: String = squareImageUrl.ifBlank { mediumImageUrl.ifBlank { imageUrl } }

    /** プレビュー用URL (medium → original のフォールバック) */
    val previewUrl: String = mediumImageUrl.ifBlank { imageUrl }

    /** AI作品かどうか */
    val isAi: Boolean = tags.any { it.equals("AI", ignoreCase = true) || it.contains("AI生成") }

    /** カードバッジテキスト (AI / manga / ページ数) */
    val cardBadgeText: String? = when {
        isAi -> "AI"
        type == "manga" -> "manga"
        pageCount > 1 -> "$pageCount"
        else -> null
    }
}

@Immutable
data class UserPreview(
    val id: Long,
    val name: String,
    val account: String,
    val profileImageUrl: String?,
    val comment: String,
    val isFollowed: Boolean,
    val previewIllusts: List<Illust>,
)

@Immutable
data class PageResult<T>(
    val items: List<T>,
    val nextUrl: String?,
)

@Immutable
data class UserProfile(
    val id: Long,
    val name: String,
    val account: String,
    val profileImageUrl: String?,
    val backgroundImageUrl: String?,
    val comment: String,
    val isFollowed: Boolean,
)

@Immutable
data class StoredAccount(
    val name: String,
    val account: String,
    val profileImageUrl: String?,
    val refreshToken: String,
    val userId: Long,
)

@Immutable
sealed interface LoadState {
    data object Idle : LoadState
    data object Loading : LoadState
    data object Loaded : LoadState
    data class Error(val message: String) : LoadState
}
