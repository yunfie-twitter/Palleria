package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.PallaSyncDao
import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** §16.5/§19.4: one bounded-memory pass, including rows beyond still-unsupported originals. */
internal class PallaSyncUnsupportedInboxRecovery(
    private val dao: PallaSyncDao,
) {
    suspend fun retry(
        chain: String,
        revalidate: suspend (PallaSyncInboxEntity) -> Boolean,
    ): Long {
        val through = dao.unsupportedInboxThroughSeq(chain) ?: return 0
        var afterSeq = -1L
        var afterId = ""
        var applied = 0L
        do {
            currentCoroutineContext().ensureActive()
            val page = dao.unsupportedInboxPage(chain, through, afterSeq, afterId, PAGE_SIZE)
            for (record in page) {
                currentCoroutineContext().ensureActive()
                if (revalidate(record)) applied++
                afterSeq = record.relaySeq
                afterId = record.recordId
            }
        } while (page.size == PAGE_SIZE)
        return applied
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
