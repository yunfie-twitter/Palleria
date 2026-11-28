package com.yunfie.illustia.platform

/** ViewModel 層のログ出力境界。プラットフォームのログ API を直接使わないための薄い窓口。 */
interface AppLogger {
    fun warn(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}

object NoopAppLogger : AppLogger {
    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = Unit
}
