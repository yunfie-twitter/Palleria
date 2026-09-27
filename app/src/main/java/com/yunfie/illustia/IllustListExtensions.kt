@file:Suppress("TooManyFunctions")

package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
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

private fun Illust.isRestricted(
    filterR18: Boolean,
    filterR18G: Boolean,
    filterAi: Boolean,
): Boolean {
    var restricted = false
    if (filterR18 && isR18) restricted = true
    if (filterR18G && isR18G) restricted = true
    if (filterAi && isAi) restricted = true
    return restricted
}

private fun NovelPreview.isRestricted(
    filterR18: Boolean,
    filterR18G: Boolean,
): Boolean {
    var restricted = false
    if (filterR18 && isR18) restricted = true
    if (filterR18G && isR18G) restricted = true
    return restricted
}

private fun List<Illust>.filterRestricted(
    filterR18: Boolean,
    filterR18G: Boolean,
    filterAi: Boolean,
): List<Illust> {
    val hasFilter = filterR18 || filterR18G || filterAi
    if (isEmpty() || !hasFilter) return this
    val hasRestricted = any { it.isRestricted(filterR18, filterR18G, filterAi) }
    return if (hasRestricted) filterNot { it.isRestricted(filterR18, filterR18G, filterAi) } else this
}

private fun List<NovelPreview>.filterRestrictedNovels(
    filterR18: Boolean,
    filterR18G: Boolean,
): List<NovelPreview> {
    val hasFilter = filterR18 || filterR18G
    if (isEmpty() || !hasFilter) return this
    val hasRestricted = any { it.isRestricted(filterR18, filterR18G) }
    return if (hasRestricted) filterNot { it.isRestricted(filterR18, filterR18G) } else this
}

@Suppress("CyclomaticComplexMethod")
internal fun IllustiaUiState.withSettings(settings: AppSettings): IllustiaUiState {
    val filter = settings.toMuteFilter()
    val updated =
        copy(
            settings = settings,
            mutedIllustsSet = filter.illustIds,
            mutedUsersSet = filter.userIds,
            mutedTagsSet = filter.tags,
        )

    val filterR18 = !settings.allowR18
    val filterR18G = settings.allowR18 && !settings.allowR18G
    val filterAi = settings.hideAiWorks
    val filterRelatedR18 = filterR18 || !settings.showR18Badge || !settings.showRelatedR18
    val filterRelatedR18G = filterR18G || filterRelatedR18
    val hasFilter = filterR18 || filterR18G || filterAi || filterRelatedR18
    if (!hasFilter) return updated

    val updatedRankingMode =
        if (updated.rankingModeItems.isEmpty()) {
            updated.rankingModeItems
        } else {
            updated.rankingModeItems.mapValues { (_, list) ->
                list.filterRestricted(filterR18, filterR18G, filterAi)
            }
        }

    return updated.copy(
        homeItems = updated.homeItems.filterRestricted(filterR18, filterR18G, filterAi),
        searchItems = updated.searchItems.filterRestricted(filterR18, filterR18G, filterAi),
        timelineItems = updated.timelineItems.filterRestricted(filterR18, filterR18G, filterAi),
        shortsFeedItems = updated.shortsFeedItems.filterRestricted(filterR18, filterR18G, filterAi),
        watchlistItems = updated.watchlistItems.filterRestricted(filterR18, filterR18G, filterAi),
        rankingItems = updated.rankingItems.filterRestricted(filterR18, filterR18G, filterAi),
        rankingModeItems = updatedRankingMode,
        relatedIllusts = updated.relatedIllusts.filterRestricted(filterRelatedR18, filterRelatedR18G, filterAi),
        bookmarkItems = updated.bookmarkItems.filterRestricted(filterR18, filterR18G, filterAi),
        selectedUserIllusts = updated.selectedUserIllusts.filterRestricted(filterR18, filterR18G, filterAi),
        selectedUserBookmarks = updated.selectedUserBookmarks.filterRestricted(filterR18, filterR18G, filterAi),
        searchNovelItems = updated.searchNovelItems.filterRestrictedNovels(filterR18, filterR18G),
        novelItems = updated.novelItems.filterRestrictedNovels(filterR18, filterR18G),
    )
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
internal fun IllustiaUiState.withUpdatedIllust(updated: Illust): IllustiaUiState {
    val updatedHome = homeItems.replaceIllustIfPresent(updated)
    val updatedSearch = searchItems.replaceIllustIfPresent(updated)
    val updatedTimeline = timelineItems.replaceIllustIfPresent(updated)
    val updatedShortsFeed = shortsFeedItems.replaceIllustIfPresent(updated)
    val updatedWatchlist = watchlistItems.replaceIllustIfPresent(updated)
    val updatedRanking = rankingItems.replaceIllustIfPresent(updated)
    var rankingModeChanged = false
    val updatedRankingModeItems =
        if (rankingModeItems.isEmpty()) {
            rankingModeItems
        } else {
            val nextMap = LinkedHashMap<String, List<Illust>>(rankingModeItems.size)
            rankingModeItems.forEach { (key, list) ->
                val replaced = list.replaceIllustIfPresent(updated)
                if (replaced !== list) {
                    rankingModeChanged = true
                }
                nextMap[key] = replaced
            }
            if (rankingModeChanged) nextMap else rankingModeItems
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
        updatedHome === homeItems &&
            updatedSearch === searchItems &&
            updatedTimeline === timelineItems &&
            updatedShortsFeed === shortsFeedItems &&
            updatedWatchlist === watchlistItems &&
            updatedRanking === rankingItems &&
            !rankingModeChanged &&
            updatedRelated === relatedIllusts &&
            updatedHistory === settings.viewHistory &&
            updatedBookmarks === bookmarkItems &&
            updatedUserIllusts === selectedUserIllusts &&
            updatedUserBookmarks === selectedUserBookmarks &&
            updatedSelected === selectedIllust

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

@JvmName("visibleIllustsWithSettings")
internal fun List<Illust>.visibleWithSettings(settings: AppSettings): List<Illust> {
    val list = visibleWith(settings.toMuteFilter())
    val r18Filtered =
        if (!settings.allowR18) {
            list.filterNot { it.isR18 }
        } else if (!settings.allowR18G) {
            list.filterNot { it.isR18G }
        } else {
            list
        }
    return if (settings.hideAiWorks) r18Filtered.filterNot { it.isAi } else r18Filtered
}

internal fun List<Illust>.visibleRelatedWithSettings(settings: AppSettings): List<Illust> {
    val base = visibleWithSettings(settings)
    return if (settings.showR18Badge && settings.showRelatedR18) {
        base
    } else {
        base.filterNot { it.isR18 || it.isR18G }
    }
}

@JvmName("visibleNovelsWithSettings")
internal fun List<NovelPreview>.visibleWithSettings(settings: AppSettings): List<NovelPreview> {
    val filter = settings.toMuteFilter()
    val userFiltered = if (filter.userIds.isEmpty()) this else filterNot { it.userId in filter.userIds }
    return if (!settings.allowR18) {
        userFiltered.filterNot { it.isR18 }
    } else if (!settings.allowR18G) {
        userFiltered.filterNot { it.isR18G }
    } else {
        userFiltered
    }
}

internal fun List<Illust>.visibleWithMutedTagsVisible(settings: AppSettings): List<Illust> {
    val filter = settings.toMuteFilter()
    val list =
        if (filter.illustIds.isEmpty() && filter.userIds.isEmpty()) {
            this
        } else {
            filterNot { illust ->
                illust.id in filter.illustIds ||
                    illust.artistId in filter.userIds
            }
        }
    val r18Filtered =
        if (!settings.allowR18) {
            list.filterNot { it.isR18 }
        } else if (!settings.allowR18G) {
            list.filterNot { it.isR18G }
        } else {
            list
        }
    return if (settings.hideAiWorks) r18Filtered.filterNot { it.isAi } else r18Filtered
}

internal fun Illust.isMutedByTags(settings: AppSettings): Boolean {
    if (settings.mutedTags.isEmpty()) return false
    val mutedTags = settings.mutedTags.toHashSet()
    return tags.any { it in mutedTags }
}
