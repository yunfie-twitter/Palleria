package com.yunfie.illustia.pallasync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.Request

/** All reads retain the policy guard and exact audience/path/query token binding (§§11.7, 17.8). */
internal class PallaSyncProbeTransport(
    private val remote: PallaSyncRemoteService,
    private val token: (HttpUrl, String, String, String, String, String) -> String?,
) {
    data class ObservedHead(
        val bytes: ByteArray,
        val head: JsonObject,
    )

    class Failure(
        val result: PallaSyncHttpResult<Nothing>,
    ) : RuntimeException()

    fun readLogHead(context: PallaSyncProbeState.Context): PallaSyncHttpResult<JsonObject> =
        remote.execute(
            request(PallaSyncUrls.logHead(context.base, context.chain), context.chain),
            migrationDestination = context.migrationDestination,
        ) {
            PallaSyncHttpResult.Success(Json.parseToJsonElement(PallaSyncCore.validateLogHead(it.pallaSyncBytes())).jsonObject)
        }

    fun observeHead(context: PallaSyncProbeState.Context): ObservedHead =
        remote
            .execute(
                request(PallaSyncUrls.transparencyHead(context.base, context.chain), context.chain),
                migrationDestination = context.migrationDestination,
            ) {
                val bytes = it.pallaSyncBytes()
                val head = Json.parseToJsonElement(PallaSyncCore.observeTransparencyHead(bytes, context.pin)).jsonObject
                PallaSyncHttpResult.Success(ObservedHead(bytes, head))
            }.requireProbeValue()

    suspend fun page(
        context: PallaSyncProbeState.Context,
        range: PallaSyncTransparencyRange,
    ): JsonObject {
        var expiredRequest: Request? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val request = request(range.url(context.base, context.chain), context.chain)
            val result =
                remote.execute(request, migrationDestination = context.migrationDestination) { response ->
                    PallaSyncHttpResult.Success(
                        Json
                            .parseToJsonElement(
                                PallaSyncCore.validateTransparencyPage(
                                    response.pallaSyncBytes(),
                                    context.pin,
                                    range.anchor.toString(),
                                    range.head.toString(),
                                    range.pageLimit,
                                ),
                            ).jsonObject,
                    )
                }
            if (result is PallaSyncHttpResult.ProtocolError && result.message == "CURSOR_EXPIRED" && range.restartExpiredCursor()) {
                expiredRequest = request
            } else {
                val page = result.requireProbeValue()
                expiredRequest?.let { remote.cursorRestarted(context.base, context.chain, it) }
                return page
            }
        }
    }

    private fun request(
        url: HttpUrl,
        chain: String,
    ): Request {
        val authorization =
            checkNotNull(token(url, chain, "GET", url.encodedPath, url.encodedQuery.orEmpty(), "")) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return Request
            .Builder()
            .url(
                url,
            ).header("Accept", PALLASYNC_MEDIA_TYPE)
            .header("Authorization", "PallaSync $authorization")
            .get()
            .build()
    }
}

internal fun <T> PallaSyncHttpResult<T>.requireProbeValue(): T =
    when (this) {
        is PallaSyncHttpResult.Success -> value
        PallaSyncHttpResult.Gone -> throw PallaSyncProbeTransport.Failure(PallaSyncHttpResult.Gone)
        is PallaSyncHttpResult.ProtocolError -> throw PallaSyncProbeTransport.Failure(this)
        is PallaSyncHttpResult.Retryable -> throw PallaSyncProbeTransport.Failure(this)
    }
