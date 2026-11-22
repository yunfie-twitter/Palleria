package com.yunfie.illustia.data.pixiv

import com.yunfie.illustia.data.IllustiaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class SuggestionStore(
    private val searchAutocomplete: suspend (String) -> List<String>,
) {
    constructor(repository: IllustiaRepository) : this({ query -> repository.searchAutocomplete(query, forceRefresh = true) })

    private val _autoWords = MutableStateFlow<List<String>>(emptyList())
    val autoWords: StateFlow<List<String>> = _autoWords.asStateFlow()
    private val requestVersion = AtomicLong()

    suspend fun fetch(
        query: String,
        debounceMillis: Long = 0L,
    ) {
        val normalizedQuery = query.trim()
        val version = requestVersion.incrementAndGet()
        if (normalizedQuery.isEmpty()) {
            _autoWords.value = emptyList()
            return
        }
        // Drop suggestions from the previous query straight away. Leaving them up during the
        // debounce and request made the list look like it never updated.
        if (lastQuery != normalizedQuery) {
            lastQuery = normalizedQuery
            _autoWords.value = emptyList()
        }
        if (debounceMillis > 0) delay(debounceMillis)
        try {
            val suggestions = searchAutocomplete(normalizedQuery)
            if (requestVersion.get() == version) {
                _autoWords.value = suggestions
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Keep the last successful suggestions for transient network failures.
        }
    }

    private var lastQuery: String? = null
}
