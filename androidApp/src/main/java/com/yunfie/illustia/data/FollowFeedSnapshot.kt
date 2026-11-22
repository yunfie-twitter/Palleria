package com.yunfie.illustia.data

import android.util.AtomicFile
import com.yunfie.illustia.models.Illust
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Bounded, disposable snapshot of followed creators' feed.
 * Never stores credentials. Used for delta calculation and instant offline rendering.
 */
internal class FollowFeedSnapshot(
    private val directory: File,
) {
    @Serializable
    data class Snapshot(
        val account: String,
        val savedAt: Long,
        val etag: String? = null,
        val items: List<Illust>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun file() = AtomicFile(File(directory, "following_feed.json"))

    private fun accountKey(token: String) =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    @Synchronized
    fun read(
        token: String,
        now: Long = System.currentTimeMillis(),
    ): Snapshot? =
        runCatching {
            if (token.isBlank()) return null
            val source = file()
            if (!source.baseFile.exists() || source.baseFile.length() > MAX_BYTES) return null
            val snapshot = json.decodeFromString<Snapshot>(source.readFully().decodeToString())
            if (snapshot.account != accountKey(token) || now - snapshot.savedAt !in 0..MAX_AGE) return null
            snapshot
        }.getOrNull()

    @Synchronized
    fun write(
        token: String,
        items: List<Illust>,
        etag: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        if (token.isBlank()) return
        runCatching {
            val bytes =
                json
                    .encodeToString(
                        Snapshot(
                            account = accountKey(token),
                            savedAt = now,
                            etag = etag,
                            items = items.take(MAX_ITEMS),
                        ),
                    ).toByteArray()
            if (bytes.size > MAX_BYTES) return
            directory.mkdirs()
            val target = file()
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
        file().delete()
    }

    private companion object {
        const val MAX_ITEMS = 60
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_AGE = 48 * 60 * 60 * 1000L
    }
}
