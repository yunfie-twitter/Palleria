package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.settings.AppSettings

internal fun List<Illust>.replaceIllustIfPresent(updated: Illust): List<Illust> {
    val index = indexOfFirst { it.id == updated.id }
    if (index < 0 || this[index] == updated) return this
    return toMutableList().also { it[index] = updated }
}

internal fun List<Illust>.replaceOrAppend(updated: Illust): List<Illust> {
    val replaced = replaceIllustIfPresent(updated)
    return if (replaced === this && none { it.id == updated.id }) listOf(updated) + this else replaced
}

internal fun List<Illust>.removeIllustIfPresent(id: Long): List<Illust> {
    val index = indexOfFirst { it.id == id }
    if (index < 0) return this
    return toMutableList().also { it.removeAt(index) }
}

internal fun List<Illust>.appendIllusts(next: List<Illust>): List<Illust> {
    if (next.isEmpty()) return this
    val existingIds = HashSet<Long>(this.size + next.size)
    this.forEach { existingIds.add(it.id) }
    return buildList(this.size + next.size) {
        addAll(this@appendIllusts)
        next.forEach { illust ->
            if (existingIds.add(illust.id)) add(illust)
        }
    }
}

internal fun IllustiaUiState.withSettings(settings: AppSettings): IllustiaUiState {
    val filter = settings.toMuteFilter()
    return copy(
        settings = settings,
        mutedIllustsSet = filter.illustIds,
        mutedUsersSet = filter.userIds,
        mutedTagsSet = filter.tags,
    )
}

internal fun IllustiaUiState.withUpdatedIllust(updated: Illust): IllustiaUiState {
    val updatedHome = homeItems.replaceIllustIfPresent(updated)
    val updatedSearch = searchItems.replaceIllustIfPresent(updated)
    val updatedTimeline = timelineItems.replaceIllustIfPresent(updated)
    val updatedShortsFeed = shortsFeedItems.replaceIllustIfPresent(updated)
    val updatedWatchlist = watchlistItems.replaceIllustIfPresent(updated)
    val updatedRanking = rankingItems.replaceIllustIfPresent(updated)
    val updatedRankingModeItems =
        rankingModeItems.mapValues { (_, list) ->
            list.replaceIllustIfPresent(updated)
        }
    val updatedRelated = relatedIllusts.replaceIllustIfPresent(updated)
    val updatedHistory = settings.viewHistory.replaceIllustIfPresent(updated)
    val updatedBookmarks =
        if (updated.isBookmarked) {
            bookmarkItems.replaceOrAppend(updated)
        } else {
            bookmarkItems.removeIllustIfPresent(updated.id)
        }
    val updatedUserIllusts = selectedUserIllusts.replaceIllustIfPresent(updated)
    val updatedUserBookmarks = selectedUserBookmarks.replaceIllustIfPresent(updated)
    val updatedSelected = if (selectedIllust?.id == updated.id) updated else selectedIllust

    val unchanged =
        listOf(
            updatedHome === homeItems,
            updatedSearch === searchItems,
            updatedTimeline === timelineItems,
            updatedShortsFeed === shortsFeedItems,
            updatedWatchlist === watchlistItems,
            updatedRanking === rankingItems,
            updatedRankingModeItems == rankingModeItems,
            updatedRelated === relatedIllusts,
            updatedHistory === settings.viewHistory,
            updatedBookmarks === bookmarkItems,
            updatedUserIllusts === selectedUserIllusts,
            updatedUserBookmarks === selectedUserBookmarks,
            updatedSelected === selectedIllust,
        ).all { it }

    if (unchanged) {
        return this
    }

    return copy(
        homeItems = updatedHome,
        searchItems = updatedSearch,
        timelineItems = updatedTimeline,
        shortsFeedItems = updatedShortsFeed,
        watchlistItems = updatedWatchlist,
        rankingItems = updatedRanking,
        rankingModeItems = updatedRankingModeItems,
        relatedIllusts = updatedRelated,
        settings = settings.copy(viewHistory = updatedHistory),
        bookmarkItems = updatedBookmarks,
        selectedUserIllusts = updatedUserIllusts,
        selectedUserBookmarks = updatedUserBookmarks,
        selectedIllust = updatedSelected,
    )
}

internal fun List<Illust>.visibleWith(filter: MuteFilter): List<Illust> {
    if (filter.isEmpty) {
        return this
    }
    return filterNot { illust ->
        illust.id in filter.illustIds ||
            illust.artistId in filter.userIds ||
            illust.tags.any { it in filter.tags }
    }
}

internal fun List<Illust>.visibleWith(state: IllustiaUiState): List<Illust> =
    visibleWith(
        MuteFilter(
            illustIds = state.mutedIllustsSet,
            userIds = state.mutedUsersSet,
            tags = state.mutedTagsSet,
        ),
    )

internal fun List<Illust>.visibleWithSettings(settings: AppSettings): List<Illust> = visibleWith(settings.toMuteFilter())

internal fun List<Illust>.visibleWithMutedTagsVisible(settings: AppSettings): List<Illust> {
    val filter = settings.toMuteFilter()
    if (filter.illustIds.isEmpty() && filter.userIds.isEmpty()) {
        return this
    }
    return filterNot { illust ->
        illust.id in filter.illustIds ||
            illust.artistId in filter.userIds
    }
}

internal fun Illust.isMutedByTags(settings: AppSettings): Boolean {
    if (settings.mutedTags.isEmpty()) return false
    val mutedTags = settings.mutedTags.toHashSet()
    return tags.any { it in mutedTags }
}
