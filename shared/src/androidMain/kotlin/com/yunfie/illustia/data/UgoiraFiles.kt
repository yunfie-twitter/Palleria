package com.yunfie.illustia.data

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** うごイラ ZIP の読み書きユーティリティ（サイズ上限付きコピー・展開・公開）。 */
object UgoiraFiles {
    private const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024

    fun copyLimited(
        input: InputStream,
        output: OutputStream,
    ) {
        input.use {
            output.use {
                var total = 0L
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_ARCHIVE_BYTES) throw PixivApiException(0, "ugoira archive exceeds the download limit")
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    fun writeEntry(
        zip: ZipFile,
        entry: ZipEntry,
        target: File,
    ) {
        target.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
    }

    fun publish(
        staging: File,
        cacheDir: File,
    ) {
        cacheDir.deleteRecursively()
        if (!staging.renameTo(cacheDir)) {
            staging.copyRecursively(cacheDir, overwrite = true)
            staging.deleteRecursively()
        }
    }
}
