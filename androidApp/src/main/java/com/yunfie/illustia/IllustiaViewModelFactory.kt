package com.yunfie.illustia

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.yunfie.illustia.platform.ViewModelDeps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Android 側で [IllustiaViewModel] を組み立てるファクトリ。
 *
 * `Application` から具体実装 [ViewModelDeps] を構築する責務をここへ切り出し、
 * ViewModel 本体をプラットフォーム非依存に保つ。
 */
class IllustiaViewModelFactory(
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
