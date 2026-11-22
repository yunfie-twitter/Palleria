package com.yunfie.illustia.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.isFeatureEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FollowDeltaSyncWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val store = SettingsStore(appContext)
            val settings = store.read()

            if (!settings.isFeatureEnabled(FeatureFlag.DeltaEtagSync)) {
                return@withContext Result.success()
            }

            if (settings.offlineWifiOnly && !isConnectedToWifi(appContext)) {
                return@withContext Result.retry()
            }

            val auth = store.readAuth()
            if (auth.refreshToken.isBlank()) {
                return@withContext Result.success()
            }

            val repository = IllustiaRepository(store)
            runCatching {
                val freshPage = repository.followingIllusts(Restrict.Public, forceRefresh = true)
                FollowDeltaSyncManager.recordSnapshotAndPrewarmDelta(
                    context = appContext,
                    token = auth.refreshToken,
                    freshItems = freshPage.items,
                    proxyBaseUrl = settings.pixivImageProxyBaseUrl,
                )
            }

            Result.success()
        }

    private fun isConnectedToWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return caps?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } ?: false
    }
}
