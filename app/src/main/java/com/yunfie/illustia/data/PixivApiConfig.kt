package com.yunfie.illustia.data

import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

internal object PixivApiConfig {
    const val CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT"
    const val CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"
    const val CLIENT_HASH_SECRET = "28c1f08f147cd7c3a4b1c08239f0a1a5"
    const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"
    const val APP_VERSION = "6.184.0"
    const val APP_OS_VERSION = "14"
    const val USER_AGENT = "PixivAndroidApp/$APP_VERSION (Android $APP_OS_VERSION;)"
}

internal fun createPixivHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .dispatcher(
        Dispatcher().apply {
            maxRequests = 12
            maxRequestsPerHost = 6
        },
    )
    .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()
