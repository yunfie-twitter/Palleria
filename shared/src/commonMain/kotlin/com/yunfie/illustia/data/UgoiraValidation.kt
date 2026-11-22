package com.yunfie.illustia.data

import com.yunfie.illustia.models.pixiv.UgoiraFrame
import java.util.zip.ZipEntry

/** うごイラ ZIP 展開時の入力検証。Rust 版 (`ugoira.rs`) と同じ制約を保つ。 */
internal object UgoiraValidation {
    fun frameNames(
        frames: List<UgoiraFrame>,
        maxEntries: Int,
    ) {
        if (frames.isEmpty() || frames.size > maxEntries) {
            throw invalid("ugoira metadata contains an invalid frame count")
        }
        val names = HashSet<String>(frames.size)
        val duplicate = frames.any { frame -> relativeIsUnsafe(frame.file) || !names.add(frame.file) }
        if (duplicate) throw invalid("ugoira metadata contains an unsafe or duplicate frame name")
    }

    fun relativePath(name: String) {
        if (relativeIsUnsafe(name)) throw invalid("ugoira archive contains an unsafe path")
    }

    /** 件数上限・重複フレーム・単一フレームサイズをまとめて検証する。 */
    fun entry(
        count: Int,
        entry: ZipEntry,
        name: String,
        required: Set<String>,
        extracted: MutableSet<String>,
    ) {
        val invalidEntry =
            count > MAX_ENTRIES ||
                (!entry.isDirectory && name in required && (!extracted.add(name) || entry.size > MAX_ENTRY_BYTES))
        if (invalidEntry) throw invalid("ugoira archive contains an invalid entry")
    }

    fun totalBytes(total: Long) {
        if (total > MAX_TOTAL_BYTES) throw invalid("ugoira archive exceeds the total size limit")
    }

    fun complete(
        required: Set<String>,
        extracted: Set<String>,
    ) {
        if (required.any { it !in extracted }) throw invalid("ugoira archive is missing required frames")
    }

    private fun relativeIsUnsafe(name: String): Boolean {
        val segments = name.split('/')
        return name.isEmpty() ||
            name.contains('\\') ||
            name.startsWith("/") ||
            name.contains(':') ||
            segments.any { it == ".." || it == "." }
    }

    private fun invalid(detail: String): PixivApiException = PixivApiException(0, detail)

    private const val MAX_ENTRIES = 10_000
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
}
