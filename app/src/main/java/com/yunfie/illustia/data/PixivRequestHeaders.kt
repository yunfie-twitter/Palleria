package com.yunfie.illustia.data

import com.yunfie.illustia.settings.currentAcceptLanguage
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import okhttp3.Request

internal fun Request.Builder.pixivOAuthHeaders(): Request.Builder {
    val clientTime = pixivClientTime()
    return addHeader("User-Agent", PixivApiConfig.USER_AGENT)
        .addHeader("App-OS", "android")
        .addHeader("App-OS-Version", PixivApiConfig.APP_OS_VERSION)
        .addHeader("App-Version", PixivApiConfig.APP_VERSION)
        .addHeader("Accept-Language", currentAcceptLanguage())
        .addHeader("X-Client-Time", clientTime)
        .addHeader("X-Client-Hash", md5(clientTime + PixivApiConfig.CLIENT_HASH_SECRET))
}

internal fun Request.Builder.pixivApiHeaders(session: PixivSession): Request.Builder {
    val clientTime = pixivClientTime()
    return addHeader("Authorization", "Bearer ${session.accessToken}")
        .addHeader("User-Agent", PixivApiConfig.USER_AGENT)
        .addHeader("App-OS", "android")
        .addHeader("App-OS-Version", PixivApiConfig.APP_OS_VERSION)
        .addHeader("App-Version", PixivApiConfig.APP_VERSION)
        .addHeader("Accept", "application/json")
        .addHeader("Accept-Language", currentAcceptLanguage())
        .addHeader("X-Client-Time", clientTime)
        .addHeader("X-Client-Hash", md5(clientTime + PixivApiConfig.CLIENT_HASH_SECRET))
        .addHeader("Referer", "https://www.pixiv.net/")
}

private fun pixivClientTime(): String =
    OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

private fun md5(value: String): String = MessageDigest.getInstance("MD5")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
