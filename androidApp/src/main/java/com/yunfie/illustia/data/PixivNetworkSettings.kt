package com.yunfie.illustia.data

import com.yunfie.illustia.models.NetworkMode
import com.yunfie.illustia.settings.AppSettings
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.InetAddress
import java.util.concurrent.TimeUnit

internal object PixivNetworkSettings {
    const val APP_API_HOST = "app-api.pixiv.net"
    const val OAUTH_HOST = "oauth.secure.pixiv.net"
    const val ACCOUNT_HOST = "accounts.pixiv.net"
    const val IMAGE_HOST = "i.pximg.net"
    const val IMAGE_STATIC_HOST = "s.pximg.net"

    // Cloudflare IPs used by pixez ECH mode.
    private val ECH_IPS = listOf("104.18.10.118", "104.18.11.118")

    private val ECH_DNS_OVERRIDES =
        mapOf(
            APP_API_HOST to ECH_IPS,
            OAUTH_HOST to ECH_IPS,
            ACCOUNT_HOST to ECH_IPS,
        )

    @JvmStatic
    fun createPixivHttpClient(
        mode: NetworkMode = NetworkMode.Standard,
        dohUrl: String? = null,
        proxySelector: InternalProxySelector? = null,
    ): OkHttpClient {
        val builder =
            OkHttpClient
                .Builder()
                .dispatcher(
                    Dispatcher().apply {
                        maxRequests = 12
                        maxRequestsPerHost = 6
                    },
                ).connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)

        if (proxySelector != null) {
            builder.proxySelector(proxySelector)
        }

        if (mode != NetworkMode.Standard) {
            builder.configureCompatibleConnection(mode)
        } else if (!dohUrl.isNullOrBlank()) {
            builder.dns(DohDns(dohUrl))
        }

        return builder.build()
    }

    private fun OkHttpClient.Builder.configureCompatibleConnection(mode: NetworkMode) {
        if (mode == NetworkMode.Ech) {
            dns(StaticDns(ECH_DNS_OVERRIDES))
        }
    }
}

private class StaticDns(
    private val overrides: Map<String, List<String>>,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        overrides[hostname]?.let { ips ->
            return ips.map { InetAddress.getByName(it) }
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}

internal fun createPixivHttpClient(mode: NetworkMode = NetworkMode.Standard): OkHttpClient =
    PixivNetworkSettings.createPixivHttpClient(mode)

internal fun createPixivHttpClient(settings: AppSettings): OkHttpClient {
    val mode =
        when (settings.pixivNetworkMode.lowercase()) {
            "compat" -> NetworkMode.Compat
            "ech" -> NetworkMode.Ech
            else -> NetworkMode.Standard
        }
    val dohUrl = DohDns.resolveUrl(settings.dohProvider, settings.dohCustomUrl)
    val proxySelector =
        InternalProxySelector.create(
            enabled = settings.internalProxyEnabled,
            type = settings.internalProxyType,
            host = settings.internalProxyHost,
            port = settings.internalProxyPort,
            bypassHostsStr = settings.internalProxyBypassHosts,
        )
    return PixivNetworkSettings.createPixivHttpClient(mode, dohUrl, proxySelector)
}
