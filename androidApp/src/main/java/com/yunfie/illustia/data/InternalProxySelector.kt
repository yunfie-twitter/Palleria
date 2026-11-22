package com.yunfie.illustia.data

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Proxy selector that routes traffic through an internal/configured proxy
 * unless the destination matches one of the bypass host patterns.
 */
class InternalProxySelector(
    private val proxy: Proxy?,
    private val bypassHosts: Set<String>,
    private val fallback: ProxySelector? = ProxySelector.getDefault(),
) : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        val host = uri?.host?.lowercase()
        val shouldUseProxy = proxy != null && host != null && !isBypassed(host)
        return if (shouldUseProxy) listOf(proxy!!) else listOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(
        uri: URI?,
        sa: SocketAddress?,
        ioe: IOException?,
    ) {
        fallback?.connectFailed(uri, sa, ioe)
    }

    private fun isBypassed(host: String): Boolean {
        val isLocal = host == "localhost" || host == "127.0.0.1" || host == "::1"
        return isLocal || bypassHosts.any { matchesPattern(host, it) }
    }

    private fun matchesPattern(
        host: String,
        pattern: String,
    ): Boolean {
        val p = pattern.trim().lowercase()
        if (p.isEmpty()) return false
        val suffix = if (p.startsWith("*.")) p.removePrefix("*.") else p
        return host == suffix || host.endsWith(".$suffix")
    }

    companion object {
        fun create(
            enabled: Boolean,
            type: String,
            host: String,
            port: Int,
            bypassHostsStr: String,
        ): InternalProxySelector {
            val trimmedHost = host.trim()
            val proxy =
                if (enabled && trimmedHost.isNotBlank() && port in 1..65535) {
                    val proxyType =
                        if (type.equals("SOCKS", ignoreCase = true)) {
                            Proxy.Type.SOCKS
                        } else {
                            Proxy.Type.HTTP
                        }
                    Proxy(proxyType, InetSocketAddress(trimmedHost, port))
                } else {
                    null
                }

            val bypassSet =
                bypassHostsStr
                    .split(',', ';', '\n')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()

            return InternalProxySelector(proxy, bypassSet)
        }
    }
}
