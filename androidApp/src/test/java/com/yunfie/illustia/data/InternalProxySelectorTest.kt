package com.yunfie.illustia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

class InternalProxySelectorTest {
    @Test
    fun select_returnsDirectWhenDisabled() {
        val selector =
            InternalProxySelector.create(
                enabled = false,
                type = "HTTP",
                host = "127.0.0.1",
                port = 8080,
                bypassHostsStr = "localhost",
            )
        val proxies = selector.select(URI("https://app-api.pixiv.net"))
        assertEquals(1, proxies.size)
        assertEquals(Proxy.NO_PROXY, proxies.first())
    }

    @Test
    fun select_returnsProxyWhenEnabled() {
        val selector =
            InternalProxySelector.create(
                enabled = true,
                type = "HTTP",
                host = "127.0.0.1",
                port = 8080,
                bypassHostsStr = "localhost",
            )
        val proxies = selector.select(URI("https://app-api.pixiv.net"))
        assertEquals(1, proxies.size)
        val proxy = proxies.first()
        assertEquals(Proxy.Type.HTTP, proxy.type())
        val address = proxy.address() as InetSocketAddress
        assertEquals("127.0.0.1", address.hostString)
        assertEquals(8080, address.port)
    }

    @Test
    fun select_bypassesConfiguredHosts() {
        val selector =
            InternalProxySelector.create(
                enabled = true,
                type = "SOCKS",
                host = "127.0.0.1",
                port = 10808,
                bypassHostsStr = "localhost, 127.0.0.1, *.pximg.net",
            )
        // Bypassed domain
        val directProxies = selector.select(URI("https://i.pximg.net/c/600x1200_90/img-master/img.jpg"))
        assertEquals(Proxy.NO_PROXY, directProxies.first())

        // Proxied domain
        val proxied = selector.select(URI("https://app-api.pixiv.net/v1/illust/detail"))
        assertEquals(Proxy.Type.SOCKS, proxied.first().type())
    }

    @Test
    fun create_handlesEmptyOrInvalidPortGracefully() {
        val selector =
            InternalProxySelector.create(
                enabled = true,
                type = "HTTP",
                host = "127.0.0.1",
                port = -1,
                bypassHostsStr = "",
            )
        val proxies = selector.select(URI("https://app-api.pixiv.net"))
        assertEquals(Proxy.NO_PROXY, proxies.first())
    }
}
