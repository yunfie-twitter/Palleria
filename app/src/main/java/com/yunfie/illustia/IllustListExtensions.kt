package com.yunfie.illustia

import com.yunfie.illustia.data.Illust
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
    return this + next.filter { existingIds.add(it.id) }
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

internal fun List<Illust>.visibleWith(state: IllustiaUiState): List<Illust> {
    return visibleWith(
        MuteFilter(
            illustIds = state.mutedIllustsSet,
            userIds = state.mutedUsersSet,
            tags = state.mutedTagsSet,
        ),
    )
}

internal fun List<Illust>.visibleWithSettings(settings: AppSettings): List<Illust> {
    return visibleWith(settings.toMuteFilter())
}
