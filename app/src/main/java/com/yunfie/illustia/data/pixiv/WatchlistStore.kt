package com.yunfie.illustia.data.pixiv

import android.content.Context
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.data.toUserMessage
import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class WatchlistStore(
    private val repository: IllustiaRepository,
    private val context: Context,
) {
    private val _state = MutableStateFlow(WatchlistState())
    val state: StateFlow<WatchlistState> = _state.asStateFlow()

    suspend fun fetch(forceRefresh: Boolean = false) {
        _state.update {
            it.copy(
                isRefreshing = forceRefresh,
                isLoading = if (it.mangaSeries.isEmpty()) true else it.isLoading,
                errorMessage = null,
            )
        }
        try {
            val model = repository.watchlistManga().withThumbnails(repository)
            _state.update {
                it.copy(
                    mangaSeries = model.series,
                    model = model,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    hasLoaded = true,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (expectedFailure: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = expectedFailure.toUserMessage(context),
                    hasLoaded = true,
                )
            }
        }
    }

    suspend fun loadMore() {
        val nextUrl = _state.value.model?.nextUrl ?: return
        if (_state.value.isPaginating) return
        _state.update { it.copy(isPaginating = true, errorMessage = null) }
        try {
            val model = repository.nextWatchlistMangaPage(nextUrl).withThumbnails(repository)
            _state.update {
                it.copy(
                    mangaSeries = it.mangaSeries + model.series,
                    model = model,
                    isPaginating = false,
                    errorMessage = null,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (expectedFailure: Exception) {
            _state.update {
                it.copy(
                    isPaginating = false,
                    errorMessage = expectedFailure.toUserMessage(context),
                )
            }
        }
    }
}

private suspend fun WatchlistMangaModel.withThumbnails(repository: IllustiaRepository): WatchlistMangaModel =
    coroutineScope {
        val series =
            series
                .map { mangaSeries ->
                    async {
                        if (mangaSeries.thumbnailUrl != null || mangaSeries.latestContentId == 0L) {
                            mangaSeries
                        } else {
                            runCatching {
                                val detail = repository.illustDetail(mangaSeries.latestContentId)
                                mangaSeries.copy(thumbnailUrl = detail.thumbnailUrl)
                            }.getOrDefault(mangaSeries)
                        }
                    }
                }.map { it.await() }
        copy(series = series)
    }
