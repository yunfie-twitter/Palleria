package com.yunfie.illustia.platform

/**
 * ViewModel 層が文字列リソースを解決するための境界。
 *
 * これまでは `AndroidViewModel` 経由で `Application.getString` を直接呼んでいたため、
 * ViewModel 層が Android のリソース API に縛られていた。ここを差し替え可能にしておくと、
 * `shared` 側からは独自実装（Compose Resources など）を注入できる。
 */
interface StringProvider {
    fun get(resId: Int): String

    fun get(
        resId: Int,
        vararg args: Any,
    ): String
}
