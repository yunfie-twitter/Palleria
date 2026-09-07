package com.yunfie.illustia

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.yunfie.illustia.account.PalleriaAccount
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.pallasync.PalleriaSyncCoordinator
import com.yunfie.illustia.platform.PlatformCapabilities
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.widget.IllustWidgetProvider
import com.yunfie.illustia.widget.RankingWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class IllustiaApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val postStartupWorkStarted = AtomicBoolean(false)

    val settingsStore: SettingsStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SettingsStore(this)
    }

    val repository: IllustiaRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        IllustiaRepository(settingsStore)
    }

    val sharedHttpClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = 8
                    maxRequestsPerHost = 4
                },
            ).connectionPool(okhttp3.ConnectionPool(4, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** The only stateful PallaSync coordinator in this application process. */
    internal val pallaSyncCoordinator: PalleriaSyncCoordinator by lazy {
        PalleriaSyncCoordinator(
            client = sharedHttpClient,
            context = this,
            coordinatorScope = appScope,
        )
    }

    override fun onCreate() {
        super.onCreate()
        CrashHandler.instance.init(this)
        appScope.launch {
            val telemetryEnabled =
                runCatching {
                    settingsStore.readStartup().sendTelemetry
                }.getOrDefault(false)
            withContext(Dispatchers.Main.immediate) {
                setTelemetryEnabled(telemetryEnabled)
            }
        }
        val appContext = applicationContext
        val cacheDirectory = cacheDir.resolve("image_cache").toOkioPath()
        val configuredCacheMb = SettingsStore.readImageCacheSizeMbSync(appContext)
        val isLowRam = PlatformCapabilities.isLowRamDevice(appContext)
        val memoryCachePercent = if (isLowRam) 0.12 else 0.20
        SingletonImageLoader.setSafe {
            ImageLoader
                .Builder(appContext)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { sharedHttpClient }))
                    if (!isLowRam) {
                        if (PlatformCapabilities.supportsAnimatedImageDecoder()) {
                            add(AnimatedImageDecoder.Factory())
                        }
                        add(GifDecoder.Factory())
                    }
                }.memoryCache {
                    MemoryCache
                        .Builder()
                        .maxSizePercent(appContext, memoryCachePercent)
                        .build()
                }.diskCache {
                    DiskCache
                        .Builder()
                        .directory(cacheDirectory)
                        .maxSizeBytes(configuredCacheMb.toLong() * 1024 * 1024)
                        .build()
                }.build()
        }
    }

    /** Starts non-critical process maintenance after the first app frame is available. */
    fun startPostStartupWork() {
        if (!postStartupWorkStarted.compareAndSet(false, true)) return

        appScope.launch {
            val appContext = applicationContext
            val recoveredPallaSync =
                runCatching {
                    pallaSyncCoordinator.recoverInterruptedActivation()
                }.getOrDefault(false)
            val settings = repository.readSettings()
            PalleriaAccount.reconcile(appContext, settings.accounts)
            launch {
                delay(6_000L)
                RankingWidgetProvider.publishPreview(appContext)
                IllustWidgetProvider.publishPreview(appContext)
            }
            setPallaSyncEnabled(recoveredPallaSync || settings.pallaSyncEnabled)
        }
    }

    fun setPallaSyncEnabled(enabled: Boolean) {
        if (enabled) {
            pallaSyncCoordinator.startBackgroundSync()
        } else {
            pallaSyncCoordinator.stopBackgroundSync()
        }
    }

    fun setTelemetryEnabled(enabled: Boolean) {
        GlitchTipTelemetry.setEnabled(applicationContext, enabled)
    }
}
