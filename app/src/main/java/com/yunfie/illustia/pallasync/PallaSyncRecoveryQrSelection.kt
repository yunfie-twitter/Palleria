package com.yunfie.illustia.pallasync

/** §18.6: scanner cancellation or invalid contents clear the previous peer descriptor. */
internal object PallaSyncRecoveryQrSelection {
    fun fromScan(contents: String?): String? =
        contents?.let { value ->
            runCatching { PallaSyncRecoveryLocalFiles.qrDescriptor(value) }.getOrNull()
        }
}
