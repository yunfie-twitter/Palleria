package com.yunfie.illustia.platform

import android.content.ContentResolver
import android.net.Uri
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.settings.AppSettings

class AndroidDocumentIo(
    private val contentResolver: ContentResolver,
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
}
