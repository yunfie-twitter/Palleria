package com.yunfie.illustia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DohDnsTest {
    @Test
    fun resolveUrl_returnsCorrectUrlsForKnownProviders() {
        assertEquals("https://cloudflare-dns.com/dns-query", DohDns.resolveUrl("cloudflare", ""))
        assertEquals("https://dns.google/dns-query", DohDns.resolveUrl("google", ""))
        assertEquals("https://dns.quad9.net/dns-query", DohDns.resolveUrl("quad9", ""))
        assertEquals("https://dns.adguard-dns.com/dns-query", DohDns.resolveUrl("adguard", ""))
    }

    @Test
    fun resolveUrl_returnsCustomUrlWhenSpecified() {
        val custom = "https://my-custom-doh.example.com/dns-query"
        assertEquals(custom, DohDns.resolveUrl("custom", custom))
    }

    @Test
    fun resolveUrl_returnsNullForSystemOrBlankCustom() {
        assertNull(DohDns.resolveUrl("system", ""))
        assertNull(DohDns.resolveUrl("custom", "   "))
        assertNull(DohDns.resolveUrl("unknown", ""))
    }
}
