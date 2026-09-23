package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.Illusts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class IllustSeriesState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPaginating: Boolean = false,
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

    suspend fun fetch(forceRefresh: Boolean = false) {
        _state.update {
            it.copy(
                isRefreshing = forceRefresh || it.illusts.isNotEmpty(),
                isLoading = it.illusts.isEmpty(),
                errorMessage = null,
            )
        }
        try {
            val model = repository.illustSeries(illustSeriesId)
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    model = model,
                    watchlistAdded = model.illustSeriesDetail?.watchlistAdded ?: false,
                    illusts = model.illusts.orEmpty(),
                    errorMessage = null,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (expectedFailure: Exception) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = expectedFailure.toString(),
                    watchlistAdded = false,
                )
            }
        }
    }

    suspend fun loadMore() {
        val nextUrl = _state.value.model?.nextUrl ?: return
        if (_state.value.isPaginating) return
        _state.update { it.copy(isPaginating = true, errorMessage = null) }
        try {
            val model = repository.nextIllustSeriesPage(nextUrl)
            _state.update {
                it.copy(
                    model = model,
                    illusts = it.illusts + model.illusts.orEmpty(),
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
                    errorMessage = expectedFailure.toString(),
                )
            }
        }
    }

    suspend fun addWatchlist() {
        try {
            repository.watchlistMangaAdd(illustSeriesId)
            _state.update { it.copy(watchlistAdded = true) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
    }

    suspend fun removeWatchlist() {
        try {
            repository.watchlistMangaDelete(illustSeriesId)
            _state.update { it.copy(watchlistAdded = false) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
        }
    }
}
