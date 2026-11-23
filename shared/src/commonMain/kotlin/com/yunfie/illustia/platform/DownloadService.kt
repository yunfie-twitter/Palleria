package com.yunfie.illustia.platform

/**
 * 再開可能なダウンロードの結果。
 *
 * `File` を境界に出さないため、保存先はパス文字列で返す。
 */
data class DownloadResult(
    /** ダウンロード済みファイルの絶対パス。 */
    val filePath: String,
    /** レスポンスの Content-Type（不明なら null）。 */
    val contentType: String?,
    /** Range リクエストで途中から再開したか。 */
    val isResumed: Boolean,
    /** 今回読み込んだ総バイト数。 */
    val bytesDownloaded: Long,
)

/**
 * ViewModel 層が必要とする HTTP ダウンロードの境界。
 *
 * `OkHttpClient` は実装側に閉じる。
 */
interface DownloadService {
    /**
     * [url] を [partPath] へダウンロードする（`.part` への再開可能ダウンロード）。
     *
     * @throws IllegalStateException HTTP エラー時。
     */
    fun downloadToPartFile(
        url: String,
        partPath: String,
        resumeEnabled: Boolean,
        headers: Map<String, String> = emptyMap(),
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): DownloadResult
}
