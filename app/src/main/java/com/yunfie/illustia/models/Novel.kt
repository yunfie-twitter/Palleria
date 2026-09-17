package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.toMuteFilter

@Immutable
data class NovelPreview(
    val id: Long,
    val title: String,
    val caption: String,
    val userId: Long,
    val userName: String,
    val userAccount: String,
    val coverUrl: String,
    val pageCount: Int,
    val textLength: Int,
    val isBookmarked: Boolean,
    val totalBookmarks: Int,
    val totalView: Int,
) {
    /** R-18作品かどうか */
    val isR18: Boolean =
        title.contains("R-18", ignoreCase = true) ||
            title.contains("R18", ignoreCase = true) ||
            title.contains("R-18G", ignoreCase = true) ||
            title.contains("R18G", ignoreCase = true) ||
            caption.contains("R-18", ignoreCase = true) ||
            caption.contains("R18", ignoreCase = true) ||
            caption.contains("R-18G", ignoreCase = true) ||
            caption.contains("R18G", ignoreCase = true)
}

fun List<NovelPreview>.visibleWithSettings(settings: AppSettings): List<NovelPreview> {
    val filter = settings.toMuteFilter()
    val userFiltered = if (filter.userIds.isEmpty()) this else filterNot { it.userId in filter.userIds }
    return if (!settings.allowR18) userFiltered.filterNot { it.isR18 } else userFiltered
}

@Immutable
data class NovelTextContent(
    val novelId: Long,
    val title: String,
    val text: String,
    val seriesPrevId: Long?,
    val seriesPrevTitle: String?,
    val seriesNextId: Long?,
    val seriesNextTitle: String?,
)

enum class NovelReadingStatus(
    val id: String,
) {
    Unread("unread"),
    Reading("reading"),
    Completed("completed"),
    Later("later"),
    ;

    companion object {
        fun fromId(id: String?): NovelReadingStatus = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Unread
    }
}

@Immutable
data class NovelReadingProgress(
    val novelId: Long,
    val lastReadPage: Int = 0,
    val totalPages: Int = 1,
    val updatedAt: Long = 0L,
    val status: NovelReadingStatus = NovelReadingStatus.Reading,
)
