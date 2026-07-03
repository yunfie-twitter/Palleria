package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.data.IllustiaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SuggestionStore(
    private val repository: IllustiaRepository,
) {
    private val _autoWords = MutableStateFlow<List<String>>(emptyList())
    val autoWords: StateFlow<List<String>> = _autoWords.asStateFlow()

    suspend fun fetch(query: String) {
        if (query.isBlank()) {
            _autoWords.value = emptyList()
            return
        }
        runCatching { repository.searchAutocomplete(query) }
            .onSuccess { _autoWords.value = it }
    }
}
