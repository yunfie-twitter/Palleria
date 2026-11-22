package com.yunfie.illustia.ui.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.yunfie.illustia.models.Illust
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal fun processUserWorksSync(
    items: List<Illust>,
    sortOrder: UserWorkSortOrder,
    typeFilter: UserWorkTypeFilter,
): List<Illust> {
    if (sortOrder == UserWorkSortOrder.Newest && typeFilter == UserWorkTypeFilter.All) {
        return items
    }
    val comparator =
        when (sortOrder) {
            UserWorkSortOrder.Newest -> compareByDescending<Illust> { it.id }
            UserWorkSortOrder.Oldest -> compareBy<Illust> { it.id }
            UserWorkSortOrder.MostBookmarks -> compareByDescending<Illust> { it.totalBookmarks }
        }
    return items
        .asSequence()
        .filter {
            when (typeFilter) {
                UserWorkTypeFilter.All -> true
                UserWorkTypeFilter.IllustOnly -> it.type != "manga"
                UserWorkTypeFilter.MangaOnly -> it.type == "manga"
            }
        }.sortedWith(comparator)
        .toList()
}

internal suspend fun processUserWorks(
    items: List<Illust>,
    sortOrder: UserWorkSortOrder,
    typeFilter: UserWorkTypeFilter,
): List<Illust> =
    withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        val comparator =
            when (sortOrder) {
                UserWorkSortOrder.Newest -> compareByDescending<Illust> { it.id }
                UserWorkSortOrder.Oldest -> compareBy<Illust> { it.id }
                UserWorkSortOrder.MostBookmarks -> compareByDescending<Illust> { it.totalBookmarks }
            }
        items
            .asSequence()
            .filter {
                context.ensureActive()
                when (typeFilter) {
                    UserWorkTypeFilter.All -> true
                    UserWorkTypeFilter.IllustOnly -> it.type != "manga"
                    UserWorkTypeFilter.MangaOnly -> it.type == "manga"
                }
            }.sortedWith { first, second ->
                context.ensureActive()
                comparator.compare(first, second)
            }.toList()
    }

internal data class UserWorkList(
    val items: List<Illust>,
    val processing: Boolean,
)

private data class ProcessedWorks(
    val source: List<Illust>,
    val sortOrder: UserWorkSortOrder,
    val typeFilter: UserWorkTypeFilter,
    val items: List<Illust>,
)

// Equal contents can arrive in a new list when a page finishes loading. Match the
// effect key to the reference used by the processing indicator.
private class WorkSourceIdentity(
    private val source: List<Illust>,
) {
    override fun equals(other: Any?): Boolean = other is WorkSourceIdentity && source === other.source

    override fun hashCode(): Int = System.identityHashCode(source)
}

@Composable
internal fun rememberUserWorks(
    userId: Long,
    items: List<Illust>,
    sortOrder: UserWorkSortOrder,
    typeFilter: UserWorkTypeFilter,
    active: Boolean,
): UserWorkList {
    val initialProcessed =
        remember(userId, WorkSourceIdentity(items), sortOrder, typeFilter) {
            ProcessedWorks(
                source = items,
                sortOrder = sortOrder,
                typeFilter = typeFilter,
                items = processUserWorksSync(items, sortOrder, typeFilter),
            )
        }
    var result by remember(userId) { mutableStateOf(initialProcessed, referentialEqualityPolicy()) }
    LaunchedEffect(userId, WorkSourceIdentity(items), sortOrder, typeFilter, active) {
        val current = result
        val upToDate = current.source === items && current.sortOrder == sortOrder && current.typeFilter == typeFilter
        if (active && !upToDate) {
            result = ProcessedWorks(items, sortOrder, typeFilter, processUserWorks(items, sortOrder, typeFilter))
        }
    }
    val current = result
    val upToDate = current.source === items && current.sortOrder == sortOrder && current.typeFilter == typeFilter
    val effectiveResult = if (upToDate) current else initialProcessed
    val processing = active && !upToDate
    return UserWorkList(effectiveResult.items, processing)
}
