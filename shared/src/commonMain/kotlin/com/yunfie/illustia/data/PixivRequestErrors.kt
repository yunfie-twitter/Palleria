package com.yunfie.illustia.data

import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.platform.StringProvider
import kotlinx.io.IOException

/**
 * プラットフォーム側から文言プロバイダを差し込むための入口。
 *
 * `shared` は `composeApp` の `AppStrings` に依存できないため、解決は
 * インストール済みの [StringProvider] に委譲する。未提供時はキー名を返す。
 */
object RequestErrorStrings {
    private var provider: StringProvider? = null

    fun install(provider: StringProvider) {
        this.provider = provider
    }

    fun get(key: StringKey): String = provider?.get(key) ?: key.name
}

/**
 * 通信失敗のユーザー向け短文を返す。
 *
 * 文言はインストール済みの [StringProvider] から解決するため、生の
 * `PixivApiException: ...` がエラーカードへ漏れることはない。
 */
fun Throwable.toUserMessage(): String {
    var current: Throwable? = this
    var statusCode: Int? = null
    while (current != null) {
        if (current is PixivApiException) {
            statusCode = current.statusCode
            break
        }
        current = current.cause
    }
    return when {
        statusCode == HTTP_UNAUTHORIZED || statusCode == HTTP_FORBIDDEN -> RequestErrorStrings.get(StringKey.ErrorForbidden)
        statusCode == HTTP_NOT_FOUND -> RequestErrorStrings.get(StringKey.ErrorNotFound)
        statusCode == HTTP_TOO_MANY_REQUESTS -> RequestErrorStrings.get(StringKey.ErrorRateLimited)
        statusCode != null && statusCode in HTTP_SERVER_ERROR_RANGE -> RequestErrorStrings.get(StringKey.ErrorServerError)
        statusCode != null -> RequestErrorStrings.get(StringKey.ErrorRequestFailed)
        this is IOException -> RequestErrorStrings.get(StringKey.ErrorServerUnreachable)
        else -> RequestErrorStrings.get(StringKey.ErrorLoadFailed)
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERROR_RANGE = 500..599
