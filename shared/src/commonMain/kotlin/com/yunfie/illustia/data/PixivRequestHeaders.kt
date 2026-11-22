package com.yunfie.illustia.data

import com.yunfie.illustia.models.PixivSession

/**
 * Pixiv API 呼び出しに付与する認証ヘッダ。
 * デバイス情報や署名 (X-Client-*) は [PixivRequestSigner] が付ける。
 */
fun pixivApiHeaders(session: PixivSession): Map<String, String> = mapOf("Authorization" to "Bearer ${session.accessToken}")
