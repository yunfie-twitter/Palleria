package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.models.pixiv.Illusts
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class IllustSeriesState(
    val isLoading: Boolean = false,
    val model: IllustSeriesWithIdModel? = null,
    val illusts: List<Illusts> = emptyList(),
    val watchlistAdded: Boolean = false,
    val errorMessage: String? = null,
)

class IllustSeriesStore(
    private val repository: IllustiaRepository,
    private val illustSeriesId: Long,
) {
    private val _state = MutableStateFlow(IllustSeriesState())
    val state: StateFlow<IllustSeriesState> = _state.asStateFlow()

    suspend fun fetch() {
        _state.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val model = repository.illustSeries(illustSeriesId)
            _state.update {
                it.copy(
                    isLoading = false,
                    model = model,
                    watchlistAdded = model.illustSeriesDetail?.watchlistAdded ?: false,
                    illusts = model.illusts.orEmpty(),
                    errorMessage = null,
                )
            }
        } catch (error: Throwable) {
            _state.update { it.copy(isLoading = false, errorMessage = error.toString(), watchlistAdded = false) }
        }
    }

    suspend fun loadMore() {
        val nextUrl = _state.value.model?.nextUrl ?: return
        try {
            val model = repository.nextIllustSeriesPage(nextUrl)
            _state.update {
                it.copy(
                    model = model,
                    illusts = it.illusts + model.illusts.orEmpty(),
                    errorMessage = null,
                )
            }
        } catch (error: Throwable) {
            _state.update { it.copy(errorMessage = error.toString()) }
        }
    }

    suspend fun addWatchlist() {
        try {
            repository.watchlistMangaAdd(illustSeriesId)
            _state.update { it.copy(watchlistAdded = true) }
        } catch (_: Throwable) {
        }
    }

    suspend fun removeWatchlist() {
        try {
            repository.watchlistMangaDelete(illustSeriesId)
            _state.update { it.copy(watchlistAdded = false) }
        } catch (_: Throwable) {
        }
    }
}
