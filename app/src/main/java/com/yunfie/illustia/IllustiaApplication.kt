package com.yunfie.illustia

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.yunfie.illustia.widget.IllustWidgetProvider
import com.yunfie.illustia.widget.RankingWidgetProvider
import kotlinx.coroutines.CoroutineScope
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.account.PalleriaAccount
import java.util.concurrent.TimeUnit

class IllustiaApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sharedHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 8
                    maxRequestsPerHost = 4
                }
            )
            .connectionPool(okhttp3.ConnectionPool(4, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        CrashHandler.instance.init(this)
        val appContext = applicationContext
        val httpClient = sharedHttpClient
        val cacheDirectory = cacheDir.resolve("image_cache").toOkioPath()
        val configuredCacheMb = SettingsStore.readImageCacheSizeMbSync(appContext)
        appScope.launch {
            PalleriaAccount.reconcile(appContext, SettingsStore(appContext).read().accounts)
            RankingWidgetProvider.publishPreview(appContext)
            IllustWidgetProvider.publishPreview(appContext)
        }
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(appContext)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
                    add(AnimatedImageDecoder.Factory())
                    add(GifDecoder.Factory())
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(appContext, 0.06)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDirectory)
                        .maxSizeBytes(configuredCacheMb.toLong() * 1024 * 1024)
                        .build()
                }
                .build()
        }
    }
}
