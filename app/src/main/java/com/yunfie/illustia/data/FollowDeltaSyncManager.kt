package com.yunfie.illustia.data

import android.content.Context
import coil3.SingletonImageLoader
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.ui.components.PixivImageHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FollowDeltaSyncResult(
    val deltaItemsCount: Int,
    val prewarmedImagesCount: Int,
    val isUpToDate: Boolean,
)

object FollowDeltaSyncManager {
    /**
     * Identifies items in [freshItems] that are not present in [cachedItems].
     * Preserves the order of newly fetched items.
     */
    fun calculateDelta(
        cachedItems: List<Illust>,
        freshItems: List<Illust>,
    ): List<Illust> {
        if (cachedItems.isEmpty()) return freshItems
        val cachedIds = cachedItems.map { it.id }.toHashSet()
        return freshItems.filterNot { cachedIds.contains(it.id) }
    }

    /**
     * Pre-warms images for [deltaItems] using Coil ImageLoader.
     * Downloads/caches thumbnail and medium preview so they are ready in disk cache.
     */
    suspend fun prewarmDeltaImages(
        context: Context,
        deltaItems: List<Illust>,
        proxyBaseUrl: String? = null,
        maxImages: Int = 10,
    ): Int =
        withContext(Dispatchers.IO) {
            if (deltaItems.isEmpty()) return@withContext 0
            val imageLoader = SingletonImageLoader.get(context)
            val targets = deltaItems.take(maxImages)
            var prewarmedCount = 0

            for (illust in targets) {
                val urls =
                    listOfNotNull(
                        illust.thumbnailUrl.takeIf { it.isNotBlank() },
                        illust.mediumImageUrl.takeIf { it.isNotBlank() && it != illust.thumbnailUrl },
                    )
                for (url in urls) {
                    runCatching {
                        val requestUrl = proxyPixivImageUrl(url, proxyBaseUrl.orEmpty())
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(requestUrl)
                                .httpHeaders(PixivImageHeaders)
                                .build()
                        imageLoader.enqueue(request)
                        prewarmedCount++
                    }
                }
            }
            prewarmedCount
        }

    /**
     * Reads the current cached snapshot, computes delta items compared to [freshItems],
     * pre-warms the newly discovered illustrations' images, and persists the updated snapshot.
     */
    suspend fun recordSnapshotAndPrewarmDelta(
        context: Context,
        token: String,
        freshItems: List<Illust>,
        proxyBaseUrl: String? = null,
        maxImages: Int = 10,
    ): Int {
        if (token.isBlank() || freshItems.isEmpty()) return 0
        val snapshotDir = java.io.File(context.cacheDir, "feed_snapshots").apply { mkdirs() }
        val snapshot = FollowFeedSnapshot(snapshotDir)
        val cached = snapshot.read(token)
        val delta = calculateDelta(cached?.items.orEmpty(), freshItems)
        val prewarmed =
            if (delta.isNotEmpty()) {
                prewarmDeltaImages(
                    context = context,
                    deltaItems = delta,
                    proxyBaseUrl = proxyBaseUrl,
                    maxImages = maxImages,
                )
            } else {
                0
            }
        snapshot.write(token, freshItems)
        return prewarmed
    }
}
