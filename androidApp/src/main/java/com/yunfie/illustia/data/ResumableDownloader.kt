package com.yunfie.illustia.data

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class ResumableDownloadResult(
    val file: File,
    val contentType: String?,
    val isResumed: Boolean,
    val bytesDownloaded: Long,
)

object ResumableDownloader {
    const val HTTP_OK = 200
    const val HTTP_PARTIAL_CONTENT = 206
    const val HTTP_RANGE_NOT_SATISFIABLE = 416

    private const val BUFFER_SIZE = 8192

    /**
     * Downloads data from [url] to [partFile].
     *
     * If [resumeEnabled] is true and [partFile] already exists with size > 0, an HTTP Range request
     * (`Range: bytes=${partFile.length()}-`) is sent to resume downloading from the existing byte offset.
     *
     * If the server responds with 206 (Partial Content), the incoming stream is appended to [partFile].
     * If the server responds with 200 (OK), [partFile] is overwritten from the beginning.
     * If the server responds with 416 (Range Not Satisfiable), [partFile] is reset and retried without Range.
     *
     * If an error occurs and [resumeEnabled] is true, [partFile] is preserved for future resumption.
     *
     * @param client The [OkHttpClient] used for network execution.
     * @param url The URL of the resource to download.
     * @param partFile The destination file storing partially or fully downloaded bytes.
     * @param resumeEnabled Whether HTTP Range resumption is enabled.
     * @param headers Optional custom headers (e.g. Referer, User-Agent).
     * @param onProgress Called with the cumulative downloaded byte count and total byte count when known.
     * @param onCall Called with each OkHttp call so callers can cancel an in-flight download.
     * @return [ResumableDownloadResult] containing metadata and completion status.
     */
    fun downloadToPartFile(
        client: OkHttpClient,
        url: String,
        partFile: File,
        resumeEnabled: Boolean,
        headers: Map<String, String> = emptyMap(),
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onCall: (Call) -> Unit = {},
    ): ResumableDownloadResult {
        val existingBytes = preparePartFile(partFile, resumeEnabled)
        val shouldRequestRange = resumeEnabled && existingBytes > 0L

        val response = executeResumableCall(client, url, partFile, shouldRequestRange, existingBytes, headers, onCall)
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Download failed with HTTP ${resp.code}: ${resp.message}")
            }

            val body = resp.body
            val contentType = body.contentType()?.toString() ?: resp.header("Content-Type")
            val append = resp.code == HTTP_PARTIAL_CONTENT && shouldRequestRange
            val startingBytes = if (append) existingBytes else 0L
            val contentLength = body.contentLength()
            val totalBytes = if (contentLength > 0L) startingBytes + contentLength else 0L
            val newlyWritten =
                writeStreamToFile(body, partFile, append, resumeEnabled) { writtenBytes ->
                    onProgress(startingBytes + writtenBytes, totalBytes)
                }

            return ResumableDownloadResult(
                file = partFile,
                contentType = contentType,
                isResumed = append,
                bytesDownloaded = newlyWritten,
            )
        }
    }

    private fun preparePartFile(
        partFile: File,
        resumeEnabled: Boolean,
    ): Long {
        partFile.parentFile?.mkdirs()
        if (!resumeEnabled && partFile.exists()) {
            partFile.delete()
        }
        return if (resumeEnabled && partFile.exists()) partFile.length() else 0L
    }

    private fun buildRequest(
        url: String,
        rangeOffset: Long?,
        headers: Map<String, String>,
    ): Request {
        val builder = Request.Builder().url(url)
        headers.forEach { (key, value) -> builder.header(key, value) }
        if (rangeOffset != null && rangeOffset > 0L) {
            builder.header("Range", "bytes=$rangeOffset-")
        }
        return builder.build()
    }

    private fun executeResumableCall(
        client: OkHttpClient,
        url: String,
        partFile: File,
        shouldRequestRange: Boolean,
        existingBytes: Long,
        headers: Map<String, String>,
        onCall: (Call) -> Unit,
    ): Response {
        val offset = if (shouldRequestRange) existingBytes else null
        var request = buildRequest(url, offset, headers)
        var call = client.newCall(request)
        onCall(call)
        var response = call.execute()

        if (shouldRequestRange && response.code == HTTP_RANGE_NOT_SATISFIABLE) {
            response.close()
            partFile.delete()
            request = buildRequest(url, null, headers)
            call = client.newCall(request)
            onCall(call)
            response = call.execute()
        }
        return response
    }

    private fun writeStreamToFile(
        body: ResponseBody,
        partFile: File,
        append: Boolean,
        resumeEnabled: Boolean,
        onBytesWritten: (Long) -> Unit,
    ): Long {
        var newlyWritten = 0L
        try {
            body.byteStream().use { input ->
                FileOutputStream(partFile, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var readBytes: Int
                    while (input.read(buffer).also { readBytes = it } != -1) {
                        output.write(buffer, 0, readBytes)
                        newlyWritten += readBytes
                        onBytesWritten(newlyWritten)
                    }
                    output.flush()
                }
            }
        } catch (ioe: IOException) {
            if (!resumeEnabled) {
                partFile.delete()
            }
            throw ioe
        }
        return newlyWritten
    }
}
