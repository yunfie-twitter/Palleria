package com.yunfie.illustia

/**
 * ViewModel モジュール連鎖の基点。
 *
 * Android では AndroidX の `ViewModel`（ライフサイクル連動）を、iOS では
 * 素のクラスを継承する。`expect` で境界を切り、連鎖の最下層だけを
 * プラットフォーム型に寄せる。
 */
expect abstract class PlatformViewModel() {
    /** 生存期間終了時のフック。Android の `ViewModel.onCleared()` に対応する。 */
    protected open fun onCleared()
}
