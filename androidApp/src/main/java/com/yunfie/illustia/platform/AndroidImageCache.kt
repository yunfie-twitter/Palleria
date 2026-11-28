package com.yunfie.illustia.platform

import android.content.Context
import coil3.SingletonImageLoader
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.yunfie.illustia.ui.components.PixivImageHeaders

class AndroidImageCache(
    private val context: Context,
) : ImageCache {
    override fun prefetch(url: String) {
        val request =
            ImageRequest
                .Builder(context)
                .data(url)
                .httpHeaders(PixivImageHeaders)
                .build()
        SingletonImageLoader.get(context).enqueue(request)
    }

    override fun clear() {
        val loader = SingletonImageLoader.get(context)
        loader.diskCache?.clear()
        loader.memoryCache?.clear()
    }
}
