package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl

/** One pinned target, current verified anchor and opaque cursor; no history-sized bookkeeping (§10.17). */
internal class PallaSyncTransparencyRange(
    val head: JsonObject,
    anchor: JsonObject,
    val pageLimit: Int,
) {
    var anchor: JsonObject = anchor
        private set
    private var cursor: String? = null
    private var rangeStart: Long? = null
    private var lastExpiredIndex: Long? = null
    val index: Long get() = anchor.getValue("index").jsonPrimitive.long
    val through: Long get() = head.getValue("entry_index").jsonPrimitive.long
    val hasRemaining: Boolean get() = index < through || cursor != null

    fun url(
        base: HttpUrl,
        chain: String,
    ): HttpUrl {
        val after = if (cursor == null) index.also { rangeStart = it } else checkNotNull(rangeStart) { "C_MISSING_DEPENDENCY" }
        return PallaSyncUrls.transparencyEntries(
            base,
            chain,
            anchor.getValue("transparency_id").jsonPrimitive.content,
            after,
            through,
            cursor,
            pageLimit,
        )
    }

    /** Expiry abandons only the opaque cursor. The verified anchor/target never changes. */
    fun restartExpiredCursor(): Boolean {
        val canRestart = cursor != null && lastExpiredIndex != index
        if (canRestart) {
            lastExpiredIndex = index
            cursor = null
            rangeStart = null
        }
        return canRestart
    }

    /** Called only after native validation and durable page persistence. */
    fun advance(
        response: JsonObject,
        next: JsonObject,
    ) {
        anchor = next
        cursor =
            response
                .getValue("next_cursor")
                .jsonPrimitive
                .takeIf { it.isString }
                ?.content
    }

    fun requireComplete() {
        check(index == through && anchor.getValue("hash") == head.getValue("transparency_hash")) { "C_TRANSPARENCY_INVALID" }
    }
}
