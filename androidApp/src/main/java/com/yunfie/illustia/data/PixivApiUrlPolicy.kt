package com.yunfie.illustia.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

private const val PIXIV_API_HOST = "app-api.pixiv.net"

internal fun String.toTrustedPixivApiUrl(): HttpUrl {
    val url = toHttpUrl()
    require(
        url.scheme == "https" &&
            url.host == PIXIV_API_HOST &&
            url.port == 443 &&
            url.username.isEmpty() &&
            url.password.isEmpty(),
    ) {
        "Untrusted Pixiv API URL"
    }
    return url
}
