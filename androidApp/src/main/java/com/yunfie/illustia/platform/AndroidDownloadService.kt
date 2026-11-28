package com.yunfie.illustia.platform

import com.yunfie.illustia.data.ResumableDownloader
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * [DownloadService] の Android 実装。
 *
 * タイムアウトを調整した共有 `OkHttpClient` を保持し、既存の [ResumableDownloader] へ委譲する。
 */
class AndroidDownloadService(
    baseClient: OkHttpClient,
) : DownloadService {
    private val client: OkHttpClient =
        baseClient
            .newBuilder()
            .readTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    override fun downloadToPartFile(
        url: String,
        partPath: String,
        resumeEnabled: Boolean,
        headers: Map<String, String>,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): DownloadResult {
        val result =
            ResumableDownloader.downloadToPartFile(
                client = client,
                url = url,
                partFile = File(partPath),
                resumeEnabled = resumeEnabled,
                headers = headers,
                onProgress = onProgress,
            )
        return DownloadResult(
            filePath = result.file.absolutePath,
            contentType = result.contentType,
            isResumed = result.isResumed,
            bytesDownloaded = result.bytesDownloaded,
        )
    }

    private companion object {
        const val DOWNLOAD_TIMEOUT_SECONDS = 30L
        const val CALL_TIMEOUT_SECONDS = 60L
    }
}
