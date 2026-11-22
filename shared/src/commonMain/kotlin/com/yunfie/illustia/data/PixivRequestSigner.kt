package com.yunfie.illustia.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Pixiv API の定数。
 */
object PixivApiConstants {
    const val CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT"
    const val CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"
    const val CLIENT_HASH_SECRET = "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"
    const val OAUTH_URL = "https://oauth.secure.pixiv.net/auth/token"
    const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"
    const val WEB_LOGIN_BASE = "https://app-api.pixiv.net/web/v1"
}

/**
 * Pixiv のモバイルアプリを模したリクエストヘッダを生成する。
 *
 * `x-client-time` / `x-client-hash` は公式アプリと同じ署名
 * （現在時刻 + 共有シークレットの MD5）で、これが無いと API 側に弾かれる。
 */
class PixivRequestSigner(
    private val userAgent: String,
    private val appOsVersion: String,
    private val acceptLanguage: String,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val baseHeaders: Map<String, String> =
        buildMap {
            put("User-Agent", userAgent)
            put("app-os", "Android")
            put("app-os-version", appOsVersion)
            put("app-version", PixivApiConfig.APP_VERSION)
            put("Accept-Language", acceptLanguage)
        }

    /**
     * [extra] にベースヘッダと署名を重ねたヘッダを返す。Authorization が付いている場合は
     * 公式アプリ準拠で `Accept: application/json` と `Referer` も付与する。
     */
    fun sign(extra: Map<String, String>): Map<String, String> {
        val authenticated = extra["Authorization"] != null
        val result = LinkedHashMap<String, String>()
        result.putAll(extra)
        result.putAll(baseHeaders)
        val clientTime = clientTime()
        result["x-client-time"] = clientTime
        result["x-client-hash"] = clientHash(clientTime)
        if (authenticated) {
            result["Accept"] = "application/json"
            result["Referer"] = "https://www.pixiv.net/"
        }
        return result
    }

    private fun clientTime(): String {
        val dateTime =
            Instant.fromEpochMilliseconds(clock()).toLocalDateTime(TimeZone.UTC)
        val month = dateTime.monthNumber.toString().padStart(2, '0')
        val day = dateTime.dayOfMonth.toString().padStart(2, '0')
        val hour = dateTime.hour.toString().padStart(2, '0')
        val minute = dateTime.minute.toString().padStart(2, '0')
        val second = dateTime.second.toString().padStart(2, '0')
        return "${dateTime.year}-$month-${day}T$hour:$minute:$second+00:00"
    }

    private fun clientHash(clientTime: String): String = md5Hex(clientTime + PixivApiConstants.CLIENT_HASH_SECRET)
}

/** MD5 の 16 進小文字表現。x-client-hash の算出に使う。 */
internal expect fun md5Hex(input: String): String
