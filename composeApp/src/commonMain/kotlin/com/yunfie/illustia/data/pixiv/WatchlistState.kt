package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel

data class WatchlistState(
    val mangaSeries: List<MangaSeriesModel> = emptyList(),
    val model: WatchlistMangaModel? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPaginating: Boolean = false,
    // The store starts empty and not loading, so without this the "no series" placeholder
    // paints on the first frame and is replaced by skeletons a frame later. The empty
    // state must only appear after a fetch has actually completed.
    val hasLoaded: Boolean = false,
)
