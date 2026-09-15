package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable
import com.yunfie.illustia.models.pixiv.IllustSeries

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
    val totalBookmarks: Int = 0,
    val totalComments: Int? = null,
    val series: IllustSeries? = null,
    val illustAiType: Int = 0,
) {
    /** サムネイル用URL (square → medium → original のフォールバック) */
    val thumbnailUrl: String = squareImageUrl.ifBlank { mediumImageUrl.ifBlank { imageUrl } }

    /** プレビュー用URL (medium → original のフォールバック) */
    val previewUrl: String = mediumImageUrl.ifBlank { imageUrl }

    /** AI作品かどうか */
    val isAi: Boolean = illustAiType == 2 || tags.any { it.equals("AI", ignoreCase = true) || it.contains("AI生成") }

    /** カードバッジテキスト (AI / manga / ページ数) */
    val cardBadgeText: String? =
        when {
            isAi -> "AI"
            type == "ugoira" -> "ugoira"
            type == "manga" -> "manga"
            pageCount > 1 -> "$pageCount"
            else -> null
        }
}
