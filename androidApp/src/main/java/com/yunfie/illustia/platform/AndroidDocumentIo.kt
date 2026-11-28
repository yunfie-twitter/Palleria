package com.yunfie.illustia.platform

import android.content.ContentResolver
import android.net.Uri
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.settings.AppSettings
import okhttp3.OkHttpClient
import okhttp3.Request

class AndroidDocumentIo(
    private val contentResolver: ContentResolver,
    private val httpClient: OkHttpClient,
    private val repository: ManagedDataRepository = ManagedDataRepository(contentResolver),
) : DocumentIo,
    DocumentStreamIo {
    override fun export(
        uri: String,
        settings: AppSettings,
    ) = repository.export(Uri.parse(uri), settings)

    override fun import(
        uri: String,
        current: AppSettings,
    ): AppSettings = repository.import(Uri.parse(uri), current)

    override fun write(
        uri: String,
        block: (java.io.OutputStream) -> Unit,
    ) {
        val output = contentResolver.openOutputStream(Uri.parse(uri), "wt")
        checkNotNull(output) { "Unable to open output stream for $uri" }
        output.use(block)
    }

    override fun downloadToUri(
        url: String,
        headers: Map<String, String>,
        uri: String,
    ) {
        val requestBuilder = Request.Builder().url(url)
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            write(uri) { output ->
                response.body.byteStream().use { it.copyTo(output) }
            }
        }
    }
}
