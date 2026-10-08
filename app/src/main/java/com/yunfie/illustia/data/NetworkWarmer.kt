package com.yunfie.illustia.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pre-resolves DNS and establishes TCP/TLS connections in the background during app startup
 * (behind the splash screen) to eliminate the initial 100-300ms network connection delay.
 */
object NetworkWarmer {
    val DEFAULT_WARM_HOSTS =
        listOf(
            "i.pximg.net",
            "s.pximg.net",
            "app-api.pixiv.net",
            "oauth.secure.pixiv.net",
        )

    private val warmed = AtomicBoolean(false)

    /**
     * Executes parallel DNS pre-resolution and HTTP/TLS socket pre-warming into [client]'s connection pool.
     */
    fun warmUp(
        client: OkHttpClient,
        proxyBaseUrl: String? = null,
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) {
        if (warmed.getAndSet(true)) return

        scope.launch(dispatcher) {
            val proxyHost = proxyBaseUrl?.toHttpUrlOrNull()?.host
            val hostsToWarm = (DEFAULT_WARM_HOSTS + listOfNotNull(proxyHost)).distinct()

            // 1. Parallel DNS Pre-resolution
            hostsToWarm.forEach { host ->
                launch(dispatcher) {
                    runCatching {
                        client.dns.lookup(host)
                    }
                }
            }

            // 2. Parallel Socket & TLS Handshake pre-warming into OkHttp ConnectionPool
            val warmUrls =
                listOf(
                    "https://i.pximg.net/",
                    "https://app-api.pixiv.net/",
                ) + listOfNotNull(proxyBaseUrl?.takeIf { it.startsWith("http") })

            warmUrls.distinct().forEach { url ->
                launch(dispatcher) {
                    runCatching {
                        val request =
                            Request
                                .Builder()
                                .url(url)
                                .header("Referer", "https://www.pixiv.net/")
                                .header("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Palleria)")
                                .head()
                                .build()
                        client.newCall(request).execute().use {
                            // Response consumed and closed; keep-alive socket remains in ConnectionPool
                        }
                    }
                }
            }
        }
    }

    internal fun resetForTesting() {
        warmed.set(false)
    }

    internal fun isWarmedForTesting(): Boolean = warmed.get()
}
