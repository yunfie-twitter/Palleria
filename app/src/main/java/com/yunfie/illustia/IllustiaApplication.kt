package com.yunfie.illustia

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

class IllustiaApplication : Application() {
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
                        .maxSizeBytes(150L * 1024 * 1024)
                        .build()
                }
                .build()
        }
    }
}
