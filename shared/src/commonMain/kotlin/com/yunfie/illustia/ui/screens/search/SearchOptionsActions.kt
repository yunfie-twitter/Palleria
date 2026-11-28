package com.yunfie.illustia.ui.screens.search

import com.yunfie.illustia.models.SearchAgeRestriction
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.SearchWorkType

/**
 * 検索オプションシートが ViewModel に要求する操作。
 */
interface SearchOptionsActions {
    fun resetSearchOptions()

    fun updateSearchSort(value: SearchSort)

    fun updateSearchTarget(value: SearchTarget)

    fun updateSearchWorkType(value: SearchWorkType)

    fun updateSearchDuration(value: SearchDuration)

    fun updateSearchBookmarkFilter(value: SearchBookmarkFilter)

    fun updateSearchAgeRestriction(value: SearchAgeRestriction)

    fun updateHideAiWorks(value: Boolean)
}
