package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.SnapshotInstallDao

/** Compares the complete verified snapshot stream with an independently verified local replay (§16.6). */
internal object PallaSyncSnapshotSemanticComparator {
    private const val PAGE_SIZE = 200

    suspend fun compareWithExactLocalReplay(
        store: PallaSyncLocalStore,
        chainId: String,
        compactionId: String,
        logId: String,
        generation: Long,
        epochHash: String,
        coveredSeq: Long,
        coveredHash: String,
    ): Boolean {
        val local = store.dao.getChainState(chainId)
        val matches =
            local != null &&
                listOf(
                    local.logId == logId,
                    local.generation == generation,
                    local.epochHash == epochHash,
                    local.verifiedSeq == coveredSeq,
                    local.materializedSeq == coveredSeq,
                    local.verifiedHash == coveredHash,
                ).all { it }
        if (!matches) return false
        val reader = PallaSyncSnapshotStateReader(store.snapshotDatabase())
        compare(store.snapshotInstallDao(), chainId, compactionId) { emit ->
            reader.stream(chainId) { entry -> emit(entry.toString()) }
        }
        return matches
    }

    suspend fun compare(
        dao: SnapshotInstallDao,
        chainId: String,
        compactionId: String,
        emitLocalState: suspend (suspend (String) -> Unit) -> Unit,
    ) {
        check(dao.install(chainId, compactionId)?.status == "verified") { "C_SNAPSHOT_INVALID" }
        var part = -1
        var entry = -1
        var page = emptyList<com.yunfie.illustia.pallasync.data.SnapshotInstallEntryEntity>()
        var pageIndex = 0

        suspend fun nextSnapshotEntry(): String? {
            if (pageIndex == page.size) {
                page = dao.entryPage(chainId, compactionId, part, entry, PAGE_SIZE)
                pageIndex = 0
            }
            val row = page.getOrNull(pageIndex++) ?: return null
            part = row.partIndex
            entry = row.entryIndex
            return row.entryJson
        }

        emitLocalState { localEntry ->
            val canonical = checkNotNull(PallaSyncCore.canonicalJson(localEntry)) { "C_SNAPSHOT_INVALID" }
            check(nextSnapshotEntry() == canonical) { "C_SNAPSHOT_INVALID" }
        }
        check(nextSnapshotEntry() == null) { "C_SNAPSHOT_INVALID" }
    }
}
