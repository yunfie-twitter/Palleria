package com.yunfie.illustia.data

import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * うごイラ ZIP のダウンロードと展開。Rust (`ugoira.rs` / `ugoira_download.rs`) の移植。
 *
 * セキュリティ上の制約（エントリ数・サイズ上限、パストラバーサル/シンボリックリンク拒否、
 * 重複エントリ拒否）は Rust 版と同じ値を保つ。
 */
internal object UgoiraCache {
    private const val MAX_ENTRIES = 10_000
    private const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
    private const val COMPLETE_MARKER = ".complete"
    private const val MIN_VALID_DELAY_MILLIS = 20
    private const val DEFAULT_DELAY_MILLIS = 100

    private val cacheLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun prepare(
        http: HttpClient,
        signer: PixivRequestSigner,
        url: String,
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback =
        withContext(Dispatchers.IO) {
            val dir = File(cacheDir)
            val lock = cacheLocks.computeIfAbsent(dir.absolutePath) { Mutex() }
            lock.withLock {
                validateFrameNames(frames)
                if (cacheIsComplete(dir, frames)) return@withLock playback(dir, frames)

                dir.parentFile?.mkdirs()
                val zipFile = File.createTempFile("ugoira", ".zip.download", dir.parentFile).apply { deleteOnExit() }
                try {
                    downloadToFile(http, signer, url, zipFile)
                    extract(dir, zipFile, frames)
                } finally {
                    zipFile.delete()
                }
                playback(dir, frames)
            }
        }

    private suspend fun downloadToFile(
        http: HttpClient,
        signer: PixivRequestSigner,
        url: String,
        target: File,
    ) {
        val response =
            http.get(url) {
                headers {
                    signer.sign(mapOf("Referer" to "https://www.pixiv.net/")).forEach { (name, value) -> header(name, value) }
                }
            }
        if (!response.status.isSuccess()) {
            throw PixivApiException(response.status.value, "ugoira download failed: ${response.status.description}")
        }
        val channel = response.bodyAsChannel()
        val buffer = channel.readRemaining(MAX_ARCHIVE_BYTES + 1).readByteArray()
        if (buffer.size > MAX_ARCHIVE_BYTES) throw archiveTooLarge()
        target.writeBytes(buffer)
    }

    private fun extract(
        cacheDir: File,
        zipFile: File,
        frames: List<UgoiraFrame>,
    ) {
        val staging = File(cacheDir.parentFile, "${cacheDir.name}.staging-${System.nanoTime()}")
        staging.mkdirs()
        try {
            ZipFile(zipFile).use { zip -> extractRequiredFrames(zip, staging, frames) }
            File(staging, COMPLETE_MARKER).createNewFile()
            UgoiraFiles.publish(staging, cacheDir)
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    private fun extractRequiredFrames(
        zip: ZipFile,
        staging: File,
        frames: List<UgoiraFrame>,
    ) {
        val required = frames.map { it.file }.toSet()
        val extracted = HashSet<String>()
        var totalBytes = 0L
        val entries = zip.entries()
        var count = 0
        while (entries.hasMoreElements()) {
            count += 1
            val entry = entries.nextElement()
            val name = entry.name.replace('\\', '/')
            UgoiraValidation.relativePath(name)
            UgoiraValidation.entry(count, entry.isDirectory, entry.size, name, required, extracted)
            if (entry.isDirectory || name !in required) continue
            totalBytes += entry.size
            UgoiraValidation.totalBytes(totalBytes)
            UgoiraFiles.writeEntry(zip, entry, File(staging, name))
        }
        UgoiraValidation.complete(required, extracted)
    }

    private fun validateFrameNames(frames: List<UgoiraFrame>) = UgoiraValidation.frameNames(frames, MAX_ENTRIES)

    private fun archiveTooLarge(): PixivApiException = PixivApiException(0, "ugoira archive exceeds the download limit")

    private fun cacheIsComplete(
        cacheDir: File,
        frames: List<UgoiraFrame>,
    ): Boolean =
        File(cacheDir, COMPLETE_MARKER).isFile &&
            frames.all { File(cacheDir, it.file).isFile }

    private fun playback(
        cacheDir: File,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback =
        UgoiraPlayback(
            frames =
                frames.map {
                    UgoiraPlaybackFrame(
                        filePath = File(cacheDir, it.file).absolutePath,
                        delayMillis = normalizedDelay(it.delay),
                    )
                },
        )

    private fun normalizedDelay(delayMillis: Int): Int = if (delayMillis >= MIN_VALID_DELAY_MILLIS) delayMillis else DEFAULT_DELAY_MILLIS
}
