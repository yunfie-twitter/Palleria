package com.yunfie.illustia

import androidx.annotation.Keep
import com.yunfie.illustia.platform.ViewModelDepsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * Stable ViewModel entry point used by the UI and dependency injection.
 *
 * Feature implementation lives under the `viewmodel` source folder so this
 * public type can remain source-compatible while its responsibilities evolve.
 *
 * `IllustiaViewModelFoundation` is a plain `ViewModel`; platform-provided
 * dependencies arrive as [ViewModelDepsApi]. The scope mirrors the old
 * `viewModelScope` contract: Main dispatcher, and cancelled once when the
 * ViewModel is cleared.
 */
@Keep
class IllustiaViewModel(
    deps: ViewModelDepsApi,
) : IllustiaViewModelCore(deps) {
    private val ownedScope: CoroutineScope = deps.scope

    override fun onCleared() {
        super.onCleared()
        ownedScope.cancel()
    }
}
