package com.yunfie.illustia

import androidx.annotation.Keep
import com.yunfie.illustia.platform.ViewModelDepsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * Android の ViewModel としてのエントリポイント。
 *
 * 状態とロジック本体は `commonMain` の [IllustiaViewModelCore] にあり、
 * ここではスコープの解放だけを行う。
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
