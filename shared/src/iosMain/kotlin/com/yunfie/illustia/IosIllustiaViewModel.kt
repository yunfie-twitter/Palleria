package com.yunfie.illustia

import com.yunfie.illustia.platform.ViewModelDepsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * iOS の ViewModel エントリポイント。
 *
 * 状態とロジック本体は `commonMain` の [IllustiaViewModelCore] にあり、ここでは
 * `MainViewController` のライフサイクルに紐づくスコープの解放だけを行う。
 */
class IosIllustiaViewModel(
    deps: ViewModelDepsApi,
) : IllustiaViewModelCore(deps) {
    private val ownedScope: CoroutineScope = deps.scope

    /** Swift 側の `viewDidLoad` から呼ぶ。 */
    fun start() = Unit

    /**
     * Swift 側から画面破棄時に呼ぶ。
     *
     * `ViewModel.onCleared()` は `protected` なので、ここから明示的に呼び出すための入口。
     */
    fun dispose() {
        onCleared()
        ownedScope.cancel()
    }
}
