package com.yunfie.illustia.platform

/**
 * ViewModel 層が文言を解決するための境界。
 *
 * `Int` のリソース ID を直接扱うと `shared` が Android の `R` に縛られるため、
 * 中立の [StringKey] を介する。プラットフォーム側でリソースへ解決する。
 */
interface StringProvider {
    fun get(key: StringKey): String

    fun get(
        key: StringKey,
        vararg args: Any,
    ): String
}
