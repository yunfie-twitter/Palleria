package com.yunfie.illustia.data

import android.os.Build
import com.yunfie.illustia.settings.currentAcceptLanguage
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import okhttp3.Request

internal fun Request.Builder.pixivOAuthHeaders(): Request.Builder {
    val clientTime = pixivClientTime()
    return addHeader("User-Agent", pixivUserAgent())
        .addHeader("App-OS", "Android")
        .addHeader("App-OS-Version", pixivAppOsVersion())
        .addHeader("App-Version", PixivApiConfig.APP_VERSION)
        .addHeader("Accept-Language", currentAcceptLanguage())
        .addHeader("X-Client-Time", clientTime)
        .addHeader("X-Client-Hash", md5(clientTime + PixivApiConfig.CLIENT_HASH_SECRET))
}

internal fun Request.Builder.pixivApiHeaders(session: PixivSession): Request.Builder {
    val clientTime = pixivClientTime()
    return addHeader("Authorization", "Bearer ${session.accessToken}")
        .addHeader("User-Agent", pixivUserAgent())
        .addHeader("App-OS", "Android")
        .addHeader("App-OS-Version", pixivAppOsVersion())
        .addHeader("App-Version", PixivApiConfig.APP_VERSION)
        .addHeader("Accept", "application/json")
        .addHeader("Accept-Language", currentAcceptLanguage())
        .addHeader("X-Client-Time", clientTime)
        .addHeader("X-Client-Hash", md5(clientTime + PixivApiConfig.CLIENT_HASH_SECRET))
        .addHeader("Referer", "https://www.pixiv.net/")
}

private fun pixivUserAgent(): String =
    "PixivAndroidApp/${PixivApiConfig.APP_VERSION} (Android ${Build.VERSION.RELEASE ?: PixivApiConfig.APP_OS_VERSION}; ${Build.MODEL ?: ""})"

private fun pixivAppOsVersion(): String =
    "Android ${Build.VERSION.RELEASE ?: PixivApiConfig.APP_OS_VERSION}"

private fun pixivClientTime(): String =
    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'+00:00'"))

private fun md5(value: String): String = MessageDigest.getInstance("MD5")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
