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
import com.yunfie.illustia.updater.AppUpdateNotificationHelper
import com.yunfie.illustia.updater.AppUpdateScheduler
import com.yunfie.illustia.widget.IllustWidgetProvider
import com.yunfie.illustia.widget.RankingWidgetProvider
import io.sentry.ITransaction
import io.sentry.SpanStatus
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

private const val WIDGET_PREVIEW_DELAY_MILLIS = 6_000L

class IllustiaApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val postStartupWorkStarted = AtomicBoolean(false)

    /**
     * Tracks the cold-start performance transaction from [onCreate] through
     * [startPostStartupWork]. Remains `null` when telemetry is disabled or when
     * [GlitchTipTelemetry] has not yet been enabled (settings are read async).
     */
    @Volatile
    private var startupTransaction: ITransaction? = null

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
                    maxRequests = 64
                    maxRequestsPerHost = 16
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
                // Begin measuring cold-start duration. startTransaction returns null
                // when telemetry is disabled, so no extra consent check is required.
                startupTransaction =
                    GlitchTipTelemetry.startTransaction("app.startup", "app.launch")
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
            withContext(Dispatchers.Main.immediate) {
                setTelemetryEnabled(settings.sendTelemetry)
            }
            PalleriaAccount.reconcile(appContext, settings.accounts)
            AppUpdateNotificationHelper.createNotificationChannel(appContext)
            AppUpdateScheduler.schedulePeriodicCheck(appContext)
            launch {
                delay(WIDGET_PREVIEW_DELAY_MILLIS)
                RankingWidgetProvider.publishPreview(appContext)
                IllustWidgetProvider.publishPreview(appContext)
            }
            setPallaSyncEnabled(recoveredPallaSync || settings.pallaSyncEnabled)
            // Mark the end of the cold-start window. finish() is a no-op when
            // startupTransaction is null (telemetry disabled or not yet enabled).
            startupTransaction?.finish(SpanStatus.OK)
            startupTransaction = null
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runCatching {
            val imageLoader = SingletonImageLoader.get(this)
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                imageLoader.memoryCache?.clear()
            } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                val currentSize = imageLoader.memoryCache?.size ?: 0L
                imageLoader.memoryCache?.trimToSize(currentSize / 2)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching {
            SingletonImageLoader.get(this).memoryCache?.clear()
        }
    }
}
