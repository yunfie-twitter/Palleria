package com.yunfie.illustia.data

import okhttp3.Headers
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pixiv API の定数。以前は Rust (`rust/pixiv-api/src/config.rs`) にあった値をそのまま Kotlin に移したもの。
 */
internal object PixivApiConstants {
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
 * Rust 版 (`headers.rs`) の移植。`x-client-time` / `x-client-hash` は公式アプリと同じ
 * 署名（現在時刻 + 共有シークレットの MD5）で、これが無いと API 側に弾かれる。
 */
internal class PixivRequestSigner(
    private val userAgent: String,
    private val appOsVersion: String,
    private val acceptLanguage: String,
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
    fun sign(extra: Headers): Headers {
        val authenticated = extra["Authorization"] != null
        val builder = Headers.Builder()
        extra.names().forEach { name -> extra.values(name).forEach { builder.add(name, it) } }
        baseHeaders.forEach { (name, value) -> builder.set(name, value) }
        val clientTime = clientTime()
        builder.set("x-client-time", clientTime)
        builder.set("x-client-hash", clientHash(clientTime))
        if (authenticated) {
            builder.set("Accept", "application/json")
            builder.set("Referer", "https://www.pixiv.net/")
        }
        return builder.build()
    }

    private fun clientTime(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun clientHash(clientTime: String): String {
        val digest =
            MessageDigest.getInstance("MD5").digest(
                (clientTime + PixivApiConstants.CLIENT_HASH_SECRET).toByteArray(Charsets.UTF_8),
            )
        return digest.joinToString("") { "%02x".format(it) }
    }
}
