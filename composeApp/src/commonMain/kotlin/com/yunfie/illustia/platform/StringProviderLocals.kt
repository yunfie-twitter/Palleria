package com.yunfie.illustia.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember

/** 共有 UI から文言を解決するための CompositionLocal。 */
val LocalStringProvider = compositionLocalOf<StringProvider?> { null }

/**
 * 共有画面が非 Composable 関数から文言を引くための入口。
 * プロバイダ未提供時はキー名を返し、表示が消えるよりデバッグしやすくする。
 */
object AppStrings {
    private var provider: StringProvider? = null

    fun install(provider: StringProvider) {
        this.provider = provider
    }

    fun get(key: StringKey): String = provider?.get(key) ?: key.name

    fun get(
        key: StringKey,
        vararg args: Any,
    ): String = provider?.get(key, *args) ?: formatStringArgs(key.name, args)
}

/** テストやプレビュー用の固定文言プロバイダ。 */
@Composable
fun rememberAppStringProvider(): StringProvider {
    val fromLocal = LocalStringProvider.current
    if (fromLocal != null) return fromLocal
    return remember { MapStringProvider(emptyMap()) }
}

/** 固定文言からなる [StringProvider]。 */
class MapStringProvider(
    private val values: Map<StringKey, String>,
) : StringProvider {
    override fun get(key: StringKey): String = values[key] ?: key.name

    override fun get(
        key: StringKey,
        vararg args: Any,
    ): String = formatStringArgs(values[key] ?: key.name, args)
}
