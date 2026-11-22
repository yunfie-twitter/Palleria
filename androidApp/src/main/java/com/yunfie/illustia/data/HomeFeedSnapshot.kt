package com.yunfie.illustia.data

import android.util.AtomicFile
import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.PageResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

internal fun computeAccountKey(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") {
        "%02x".format(it)
    }

/** A bounded, disposable first-page snapshot. Never contains OAuth credentials. */
internal class HomeFeedSnapshot(
    private val directory: File,
) {
    @Serializable
    private data class Snapshot(
        val account: String,
        val savedAt: Long,
        val items: List<Illust>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(kind: HomeFeedKind) = AtomicFile(File(directory, "${kind.name}.json"))

    @Synchronized
    fun readWithAccountHash(
        kind: HomeFeedKind,
        accountHash: String,
        now: Long = System.currentTimeMillis(),
    ): PageResult<Illust>? =
        runCatching {
            if (accountHash.isBlank()) return null
            val source = file(kind)
            if (source.baseFile.length() > MAX_BYTES) return null
            val snapshot = json.decodeFromString<Snapshot>(source.readFully().decodeToString())
            if (snapshot.account != accountHash || now - snapshot.savedAt !in 0..MAX_AGE) return null
            // Pagination belongs to the fresh response, not yesterday's first page.
            PageResult(snapshot.items.take(MAX_ITEMS), null)
        }.getOrNull()

    @Synchronized
    fun read(
        kind: HomeFeedKind,
        token: String,
        now: Long = System.currentTimeMillis(),
    ): PageResult<Illust>? = readWithAccountHash(kind, computeAccountKey(token), now)

    @Synchronized
    fun write(
        kind: HomeFeedKind,
        token: String,
        page: PageResult<Illust>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (token.isBlank()) return
        runCatching {
            val bytes = json.encodeToString(Snapshot(computeAccountKey(token), now, page.items.take(MAX_ITEMS))).toByteArray()
            if (bytes.size > MAX_BYTES) return
            directory.mkdirs()
            val target = file(kind)
            val stream = target.startWrite()
            try {
                stream.write(bytes)
                target.finishWrite(stream)
            } catch (failure: java.io.IOException) {
                target.failWrite(stream)
                throw failure
            }
        }
    }

    @Synchronized
    fun clear() {
        HomeFeedKind.entries.forEach { file(it).delete() }
    }

    private companion object {
        const val MAX_ITEMS = 60
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_AGE = 24 * 60 * 60 * 1000L
    }
}
