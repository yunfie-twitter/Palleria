package com.yunfie.illustia.data

import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS over HTTPS (DoH) resolver supporting RFC 8484 standard.
 * Resolves hostnames securely via HTTPS with in-memory caching and fallback to system DNS.
 */
class DohDns(
    private val dohUrl: String,
    private val bootstrapClient: OkHttpClient = createBootstrapClient(),
    private val fallbackDns: Dns = Dns.SYSTEM,
) : Dns {
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expiresAtMs: Long,
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val cached = getCachedAddresses(hostname)
        return cached ?: resolveAddresses(hostname)
    }

    private fun getCachedAddresses(hostname: String): List<InetAddress>? {
        val now = System.currentTimeMillis()
        val entry = cache[hostname] ?: return null
        return if (now < entry.expiresAtMs) {
            entry.addresses
        } else {
            cache.remove(hostname)
            null
        }
    }

    private fun resolveAddresses(hostname: String): List<InetAddress> {
        val bootstrapIps = KNOWN_BOOTSTRAP_HOSTS[hostname.lowercase()]
        if (bootstrapIps != null) {
            return bootstrapIps.map { InetAddress.getByName(it) }
        }

        return try {
            val addresses = queryDoh(hostname)
            if (addresses.isNotEmpty()) {
                val now = System.currentTimeMillis()
                cache[hostname] = CacheEntry(addresses, now + DEFAULT_TTL_MS)
                addresses
            } else {
                fallbackDns.lookup(hostname)
            }
        } catch (_: Exception) {
            fallbackDns.lookup(hostname)
        }
    }

    private fun queryDoh(hostname: String): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        queryRecordType(hostname, TYPE_A)?.let { addresses.addAll(it) }
        queryRecordType(hostname, TYPE_AAAA)?.let { addresses.addAll(it) }
        return addresses
    }

    private fun queryRecordType(
        hostname: String,
        qtype: Int,
    ): List<InetAddress>? {
        val queryBytes = buildDnsQuery(hostname, qtype)
        val request =
            Request
                .Builder()
                .url(dohUrl)
                .header("Accept", DNS_MESSAGE_MEDIA_TYPE)
                .post(queryBytes.toRequestBody(DNS_MESSAGE_MEDIA_TYPE.toMediaType()))
                .build()

        val response = runCatching { bootstrapClient.newCall(request).execute() }.getOrNull() ?: return null
        return response.use { res ->
            if (res.isSuccessful) {
                res.body?.bytes()?.let { parseDnsResponse(it) }
            } else {
                null
            }
        }
    }

    private fun buildDnsQuery(
        hostname: String,
        qtype: Int,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeShort(QUERY_ID)
        dos.writeShort(STANDARD_QUERY_FLAGS)
        dos.writeShort(1)
        dos.writeShort(0)
        dos.writeShort(0)
        dos.writeShort(0)

        for (part in hostname.split('.')) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)

        dos.writeShort(qtype)
        dos.writeShort(1)

        dos.flush()
        return baos.toByteArray()
    }

    private fun parseDnsResponse(data: ByteArray): List<InetAddress> {
        if (data.size < HEADER_SIZE) return emptyList()
        val dis = DataInputStream(data.inputStream())

        dis.skipBytes(SKIP_HEADER_PREFIX_BYTES)
        val qdCount = dis.readUnsignedShort()
        val anCount = dis.readUnsignedShort()
        dis.skipBytes(SKIP_HEADER_SUFFIX_BYTES)

        for (i in 0 until qdCount) {
            skipName(dis)
            dis.skipBytes(SKIP_QUESTION_TRAIL_BYTES)
        }

        val addresses = mutableListOf<InetAddress>()
        for (i in 0 until anCount) {
            skipName(dis)
            val type = dis.readUnsignedShort()
            dis.skipBytes(SKIP_CLASS_AND_TTL_BYTES)
            val rdLength = dis.readUnsignedShort()

            if (type == TYPE_A && rdLength == IPV4_LENGTH) {
                val ipBytes = ByteArray(IPV4_LENGTH)
                dis.readFully(ipBytes)
                addresses.add(InetAddress.getByAddress(ipBytes))
            } else if (type == TYPE_AAAA && rdLength == IPV6_LENGTH) {
                val ipBytes = ByteArray(IPV6_LENGTH)
                dis.readFully(ipBytes)
                addresses.add(InetAddress.getByAddress(ipBytes))
            } else {
                dis.skipBytes(rdLength)
            }
        }

        return addresses
    }

    private fun skipName(dis: DataInputStream) {
        var len = dis.readUnsignedByte()
        while (len > 0) {
            if ((len and POINTER_MASK) == POINTER_MASK) {
                dis.skipBytes(1)
                return
            }
            dis.skipBytes(len)
            len = dis.readUnsignedByte()
        }
    }

    companion object {
        private const val DNS_MESSAGE_MEDIA_TYPE = "application/dns-message"
        private const val QUERY_ID = 0x1A2B
        private const val STANDARD_QUERY_FLAGS = 0x0100
        private const val HEADER_SIZE = 12
        private const val SKIP_HEADER_PREFIX_BYTES = 4
        private const val SKIP_HEADER_SUFFIX_BYTES = 4
        private const val SKIP_QUESTION_TRAIL_BYTES = 4
        private const val SKIP_CLASS_AND_TTL_BYTES = 6
        private const val POINTER_MASK = 0xC0
        private const val TYPE_A = 1
        private const val TYPE_AAAA = 28
        private const val IPV4_LENGTH = 4
        private const val IPV6_LENGTH = 16
        private const val DEFAULT_TTL_MS = 300_000L
        private const val BOOTSTRAP_CONNECT_TIMEOUT_SEC = 5L
        private const val BOOTSTRAP_IO_TIMEOUT_SEC = 8L
        private const val BOOTSTRAP_CALL_TIMEOUT_SEC = 10L

        val KNOWN_PROVIDERS =
            mapOf(
                "cloudflare" to "https://cloudflare-dns.com/dns-query",
                "google" to "https://dns.google/dns-query",
                "quad9" to "https://dns.quad9.net/dns-query",
                "adguard" to "https://dns.adguard-dns.com/dns-query",
            )

        private val KNOWN_BOOTSTRAP_HOSTS =
            mapOf(
                "cloudflare-dns.com" to listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111"),
                "dns.google" to listOf("8.8.8.8", "8.8.4.4", "2001:4860:4860::8888"),
                "dns.quad9.net" to listOf("9.9.9.9", "149.112.112.112", "2620:fe::fe"),
                "dns.adguard-dns.com" to listOf("94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff"),
            )

        fun createBootstrapClient(): OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(BOOTSTRAP_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(BOOTSTRAP_IO_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(BOOTSTRAP_IO_TIMEOUT_SEC, TimeUnit.SECONDS)
                .callTimeout(BOOTSTRAP_CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build()

        fun resolveUrl(
            provider: String,
            customUrl: String,
        ): String? =
            when (provider) {
                "cloudflare" -> KNOWN_PROVIDERS["cloudflare"]
                "google" -> KNOWN_PROVIDERS["google"]
                "quad9" -> KNOWN_PROVIDERS["quad9"]
                "adguard" -> KNOWN_PROVIDERS["adguard"]
                "custom" -> customUrl.trim().takeIf { it.isNotBlank() }
                else -> null
            }
    }
}
