package com.yunfie.illustia.platform

import android.net.Uri
import com.yunfie.illustia.data.ManagedDataRepository
import com.yunfie.illustia.settings.AppSettings

class AndroidDocumentIo(
    private val repository: ManagedDataRepository,
) : DocumentIo {
    override fun export(
        uri: String,
        settings: AppSettings,
    ) = repository.export(Uri.parse(uri), settings)

    override fun import(
        uri: String,
        current: AppSettings,
    ): AppSettings = repository.import(Uri.parse(uri), current)
}
