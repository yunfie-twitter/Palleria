package com.yunfie.illustia.pallasync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** §18.6: scanner cancellation or invalid contents clear the previous peer descriptor. */
internal class PallaSyncRecoveryQrSelection {
    var descriptor: String? by mutableStateOf(null)
        private set

    /** A rescan starts a new trust decision; the previous scan is never a fallback. */
    fun beginScan() {
        descriptor = null
    }

    fun selectFile() {
        descriptor = null
    }

    /** Returns true only for a non-cancelled result that failed descriptor validation. */
    fun completeScan(contents: String?): Boolean {
        descriptor = fromScan(contents)
        return contents != null && descriptor == null
    }

    companion object {
        fun fromScan(contents: String?): String? =
            contents?.let { value ->
                runCatching { PallaSyncRecoveryLocalFiles.qrDescriptor(value) }.getOrNull()
            }
    }
}
