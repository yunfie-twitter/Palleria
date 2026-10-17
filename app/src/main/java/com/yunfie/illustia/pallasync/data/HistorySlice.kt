package com.yunfie.illustia.pallasync.data

/** Local keyset cursor bound to an index revision; never serialized as a relay cursor. */
data class HistoryCursor(
    val revision: Long,
    val key: ByteArray,
    val entityId: String,
)

data class HistorySlice(
    val rows: List<HistoryIndexEntity>,
    val next: HistoryCursor?,
    val revision: Long,
)
