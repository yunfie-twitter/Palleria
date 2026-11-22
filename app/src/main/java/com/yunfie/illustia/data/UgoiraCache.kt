package com.yunfie.illustia.data

import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
        okHttp: OkHttpClient,
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
                    downloadToFile(okHttp, signer, url, zipFile)
                    extract(dir, zipFile, frames)
                } finally {
                    zipFile.delete()
                }
                playback(dir, frames)
            }
        }

    private fun downloadToFile(
        okHttp: OkHttpClient,
        signer: PixivRequestSigner,
        url: String,
        target: File,
    ) {
        val request =
            Request
                .Builder()
                .url(url)
                .headers(
                    signer.sign(
                        okhttp3.Headers
                            .Builder()
                            .add("Referer", "https://www.pixiv.net/")
                            .build(),
                    ),
                ).get()
                .build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PixivApiException(response.code, "ugoira download failed: ${response.message}")
            }
            val body = response.body
            if (body.contentLength() > MAX_ARCHIVE_BYTES) throw archiveTooLarge()
            UgoiraFiles.copyLimited(body.byteStream(), target.outputStream())
        }
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
            UgoiraValidation.entry(count, entry, name, required, extracted)
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
