package com.yunfie.illustia.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient

/**
 * OkHttp の設定（DoH / プロキシ / ECH 用 DNS / タイムアウト）を保ったまま
 * Ktor の Http クライアントへ橋渡しする。
 */
fun createKtorPixivClient(okHttp: OkHttpClient): HttpClient = createPixivKtorClient(OkHttp.create { preconfigured = okHttp })
