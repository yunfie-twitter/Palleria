package com.yunfie.illustia.data

import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** 署名付きリクエストの既定ヘッダを作る。プラットフォーム情報は呼び出し側が渡す。 */
fun defaultSigner(
    userAgent: String = "PixivAndroidApp/${PixivApiConfig.APP_VERSION}",
    appOsVersion: String = "Android",
    acceptLanguage: String = "ja-JP",
): PixivRequestSigner = PixivRequestSigner(userAgent, appOsVersion, acceptLanguage)

/** Pixiv API 用の Ktor クライアント。エンジンはプラットフォーム側から注入する。 */
fun createPixivKtorClient(
    engine: HttpClientEngine,
    json: Json = PixivApiJson.json,
): HttpClient =
    HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 12_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 20_000
        }
    }
