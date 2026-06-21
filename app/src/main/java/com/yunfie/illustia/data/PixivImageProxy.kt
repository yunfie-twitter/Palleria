package com.yunfie.illustia.data

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class PixivImageProxy(
    val name: String,
    val baseUrl: String,
)

val PixivImageProxyOptions = listOf(
    PixivImageProxy("ducks.party", "https://pixiv.ducks.party"),
    PixivImageProxy("cocomi.eu.org", "https://pximg.cocomi.eu.org"),
    PixivImageProxy("suimoe.com", "https://i.suimoe.com"),
    PixivImageProxy("yuki.sh", "https://i.yuki.sh"),
    PixivImageProxy("obfs.dev", "https://pximg.obfs.dev"),
    PixivImageProxy("darkness.services", "https://pixiv.darkness.services"),
    PixivImageProxy("tatakai.top", "https://pixiv.tatakai.top"),
    PixivImageProxy("169889.xyz", "https://pi.169889.xyz"),
    PixivImageProxy("pixiv.re", "https://i.pixiv.re"),
)

fun proxyPixivImageUrl(url: String, proxyBaseUrl: String): String {
    if (url.isBlank() || proxyBaseUrl.isBlank()) return url

    val source = runCatching { Uri.parse(url) }.getOrNull() ?: return url
    if (!source.host.isPixivImageHost()) return url

    val proxy = runCatching { Uri.parse(proxyBaseUrl.trim().trimEnd('/')) }.getOrNull() ?: return url
    if (proxy.scheme.isNullOrBlank() || proxy.host.isNullOrBlank()) return url

    return source.buildUpon()
        .scheme(proxy.scheme)
        .authority(proxy.encodedAuthority)
        .build()
        .toString()
}

private fun String?.isPixivImageHost(): Boolean {
    return this == "i.pximg.net" || this == "i-f.pximg.net"
}
