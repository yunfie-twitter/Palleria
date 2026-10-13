package com.yunfie.illustia.pallasync

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.pallasync.data.HistoryCursor

/** Local display result, never a relay/wire model (§16.5). */
data class PallaSyncHistoryPage(
    val items: List<Illust>,
    val next: HistoryCursor?,
    val partial: Boolean,
)
