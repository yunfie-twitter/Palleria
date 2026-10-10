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
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.isFeatureEnabled
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
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val WIDGET_PREVIEW_DELAY_MILLIS = 6_000L
private const val BACKGROUND_SCHEDULER_STARTUP_DELAY_MILLIS = 4_000L

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
        val isLowRam = PlatformCapabilities.isLowRamDevice(this)
        val maxConcurrentRequests = if (isLowRam) 16 else 64
        val maxConcurrentPerHost = if (isLowRam) 6 else 16
        val connectionPoolSize = if (isLowRam) 8 else 16
        OkHttpClient
            .Builder()
            .dispatcher(
                Dispatcher().apply {
                    maxRequests = maxConcurrentRequests
                    maxRequestsPerHost = maxConcurrentPerHost
                },
            ).connectionPool(okhttp3.ConnectionPool(connectionPoolSize, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** The only stateful PallaSync coordinator in this application process. */
    private val pallaSyncCoordinatorDelegate =
        lazy {
            PalleriaSyncCoordinator(
                client = sharedHttpClient,
                context = this,
                coordinatorScope = appScope,
            )
        }

    internal val pallaSyncCoordinator: PalleriaSyncCoordinator by pallaSyncCoordinatorDelegate

    override fun onCreate() {
        super.onCreate()
        CrashHandler.instance.init(this)
        appScope.launch {
            val startupSettings = repository.readStartupSettings()
            SettingsStore.updateImageCacheSizeMbCache(startupSettings.imageCacheSizeMb)
            if (startupSettings.isFeatureEnabled(FeatureFlag.PreDnsSocketWarming)) {
                com.yunfie.illustia.data.NetworkWarmer
                    .warmUp(sharedHttpClient, scope = appScope)
            }
        }
        SingletonImageLoader.setSafe {
            val appContext = applicationContext
            val cacheDirectory = cacheDir.resolve("image_cache").toOkioPath()
            val configuredCacheMb = SettingsStore.readImageCacheSizeMbSync(appContext)
            val isLowRam = PlatformCapabilities.isLowRamDevice(appContext)
            val memoryCachePercent = PlatformCapabilities.recommendedCoilMemoryCachePercent(appContext)
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
                        .weakReferencesEnabled(!isLowRam)
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
            val settings = repository.readStartupMaintenanceSettings()
            setTelemetryEnabled(settings.sendTelemetry)
            val accounts = repository.readAccountsForStartupMaintenance()
            val recoveredPallaSync =
                if (settings.pallaSyncEnabled) {
                    runCatching {
                        pallaSyncCoordinator.recoverInterruptedActivation()
                    }.getOrDefault(false)
                } else {
                    false
                }
            PalleriaAccount.reconcile(appContext, accounts)
            AppUpdateNotificationHelper.createNotificationChannel(appContext)
            delay(BACKGROUND_SCHEDULER_STARTUP_DELAY_MILLIS)
            if (settings.checkUpdatesInBackground && (settings.notifyNewVersion || settings.autoDownloadUpdates)) {
                AppUpdateScheduler.schedulePeriodicCheck(appContext)
            } else {
                AppUpdateScheduler.cancelPeriodicCheck(appContext)
            }
            com.yunfie.illustia.data.FollowDeltaSyncScheduler
                .schedulePeriodicSync(appContext)
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
        } else if (pallaSyncCoordinatorDelegate.isInitialized()) {
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
