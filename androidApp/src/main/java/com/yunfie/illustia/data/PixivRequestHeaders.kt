package com.yunfie.illustia.data

import com.yunfie.illustia.models.PixivSession
import okhttp3.Request

internal fun Request.Builder.pixivApiHeaders(session: PixivSession): Request.Builder {
    // Device, locale, app and signed X-Client-* headers are generated in Rust.
    return addHeader("Authorization", "Bearer ${session.accessToken}")
}
