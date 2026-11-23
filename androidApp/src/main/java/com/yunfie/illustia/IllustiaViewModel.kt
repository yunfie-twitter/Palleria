package com.yunfie.illustia

import android.app.Application
import androidx.annotation.Keep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yunfie.illustia.platform.ViewModelDeps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Stable ViewModel entry point used by the UI and dependency injection.
 *
 * Feature implementation lives under the `viewmodel` source folder so this
 * public type can remain source-compatible while its responsibilities evolve.
 *
 * `IllustiaViewModelFoundation` is a plain `ViewModel`; the Android `Application`
 * and the lifetime-bound coroutine scope are supplied through [ViewModelDeps].
 * The scope mirrors the old `viewModelScope` contract: Main dispatcher, and
 * cancelled once when the ViewModel is cleared.
 */
@Keep
class IllustiaViewModel(
    deps: ViewModelDeps,
) : IllustiaViewModelCore(deps) {
    private val ownedScope: CoroutineScope = deps.scope

    override fun onCleared() {
        super.onCleared()
        ownedScope.cancel()
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(IllustiaViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            @Suppress("UNCHECKED_CAST")
            return IllustiaViewModel(
                ViewModelDeps(application = application, scope = scope),
            ) as T
        }
    }
}
