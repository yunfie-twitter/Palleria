package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class WatchlistState(
    val mangaSeries: List<MangaSeriesModel> = emptyList(),
    val model: WatchlistMangaModel? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
)

class WatchlistStore(
    private val repository: IllustiaRepository,
) {
    private val _state = MutableStateFlow(WatchlistState())
    val state: StateFlow<WatchlistState> = _state.asStateFlow()

    suspend fun fetch() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val model = repository.watchlistManga()
            _state.update {
                it.copy(
                    mangaSeries = model.series,
                    model = model,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        } catch (error: Throwable) {
            _state.update { it.copy(isLoading = false, errorMessage = error.toString()) }
        }
    }

    suspend fun loadMore() {
        val nextUrl = _state.value.model?.nextUrl ?: return
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val model = repository.nextWatchlistMangaPage(nextUrl)
            _state.update {
                it.copy(
                    mangaSeries = it.mangaSeries + model.series,
                    model = model,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        } catch (error: Throwable) {
            _state.update { it.copy(isLoading = false, errorMessage = error.toString()) }
        }
    }
}
