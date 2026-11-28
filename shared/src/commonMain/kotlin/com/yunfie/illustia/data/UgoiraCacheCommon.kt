package com.yunfie.illustia.data

import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame
import com.yunfie.illustia.models.pixiv.normalizedUgoiraDelayMillis
import com.yunfie.illustia.platform.FileSystem
import com.yunfie.illustia.platform.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readByteArray

/**
 * うごイラ ZIP を展開してフレームファイルを取り出す境界。
 *
 * ZIP の読み方はプラットフォームのアーカイブ API に依る（Android は `ZipFile`、iOS は
 * `NSFileManager` が扱える形式へ変換する）。検証（エントリ数・パス・サイズ上限・重複）は
 * 呼び出し側の [UgoiraCacheCommon] が共通で行うので、実装は「必要なエントリだけ書き出す」
 * ことに責務を絞る。
 */
interface UgoiraArchive {
    /**
     * [archivePath] の ZIP から [requiredFiles] に一致するエントリを [stagingDir] へ書き出す。
     *
     * 検証フックは呼ばない。エントリの妥当性（件数・パス・重複・サイズ）は ZIP を読む側が
     * エントリごとに知り得る情報なので、[UgoiraArchive.extract] の実装内で
     * [UgoiraValidation] を呼ぶこと。
     */
    fun extract(
        archivePath: String,
        stagingDir: String,
        requiredFiles: Set<String>,
    )
}

/**
 * うごイラ ZIP のダウンロードとキャッシュ。Rust (`ugoira.rs` / `ugoira_download.rs`) の移植。
 *
 * セキュリティ上の制約（エントリ数・サイズ上限、パストラバーサル/シンボリックリンク拒否、
 * 重複エントリ拒否）は Rust 版と同じ値を保つ。
 */
class UgoiraCacheCommon(
    private val fileSystem: FileSystem,
    private val archive: UgoiraArchive,
) {
    private val cacheLocks = mutableMapOf<String, Mutex>()
    private val lockGuard = kotlinx.atomicfu.locks.SynchronizedObject()

    suspend fun prepare(
        http: HttpClient,
        signer: PixivRequestSigner,
        url: String,
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback {
        val lock = lockFor(cacheDir)
        return lock.withLock {
            UgoiraValidation.frameNames(frames, MAX_ENTRIES)
            if (cacheIsComplete(cacheDir, frames)) return@withLock playback(cacheDir, frames)

            val parent = cacheDir.substringBeforeLast('/', "")
            if (parent.isNotEmpty()) fileSystem.makeDirectories(parent)
            val zipPath = fileSystem.createTempFile("ugoira", ".zip.download", parent.ifEmpty { cacheDir })
            try {
                downloadToFile(http, signer, url, zipPath)
                extract(cacheDir, zipPath, frames)
            } finally {
                fileSystem.delete(zipPath)
            }
            playback(cacheDir, frames)
        }
    }

    private fun lockFor(key: String): Mutex =
        kotlinx.atomicfu.locks.synchronized(lockGuard) {
            cacheLocks.getOrPut(key) { Mutex() }
        }

    private suspend fun downloadToFile(
        http: HttpClient,
        signer: PixivRequestSigner,
        url: String,
        targetPath: String,
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
        if (buffer.size > MAX_ARCHIVE_BYTES) throw PixivApiException(0, "ugoira archive exceeds the download limit")
        fileSystem.openWrite(targetPath).use { it.write(buffer) }
    }

    private fun extract(
        cacheDir: String,
        zipPath: String,
        frames: List<UgoiraFrame>,
    ) {
        val staging = "$cacheDir.staging-${currentTimeMillis()}"
        fileSystem.makeDirectories(staging)
        try {
            val required = frames.map { it.file }.toSet()
            archive.extract(zipPath, staging, required)
            fileSystem.openWrite("$staging/$COMPLETE_MARKER").close()
            fileSystem.delete(cacheDir)
            fileSystem.copy(staging, cacheDir)
        } finally {
            if (fileSystem.stat(staging) != null) fileSystem.delete(staging)
        }
    }

    private fun cacheIsComplete(
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): Boolean =
        fileSystem.stat("$cacheDir/$COMPLETE_MARKER")?.let { !it.isDirectory } == true &&
            frames.all { fileSystem.stat("$cacheDir/${it.file}")?.let { entry -> !entry.isDirectory } == true }

    private fun playback(
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback =
        UgoiraPlayback(
            frames =
                frames.map {
                    UgoiraPlaybackFrame(
                        filePath = "$cacheDir/${it.file}",
                        delayMillis = normalizedUgoiraDelayMillis(it.delay).toInt(),
                    )
                },
        )

    private companion object {
        const val MAX_ENTRIES = 10_000
        const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024
        const val COMPLETE_MARKER = ".complete"
    }
}
