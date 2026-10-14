package com.yunfie.illustia.pallasync

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** §7.11.5: user-selected local files carry original public objects or encrypted signed transfers.
 * The caller verifies received bytes in the native stage before consuming them. No wire wrapper is added.
 */
internal object PallaSyncRecoveryLocalFiles {
    internal const val MAX_TRANSFER_BYTES = 4_194_304
    private const val BUFFER_BYTES = 8192

    fun read(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val next = input.read()
                if (next < 0) return decode(output.toByteArray())
                check(output.size() < MAX_TRANSFER_BYTES) { "C_RECOVERY_SHARE_INVALID" }
                output.write(next)
            } else {
                check(count <= MAX_TRANSFER_BYTES - output.size()) { "C_RECOVERY_SHARE_INVALID" }
                output.write(buffer, 0, count)
            }
        }
        return decode(output.toByteArray())
    }

    fun write(
        output: OutputStream,
        original: String,
    ) {
        val bytes = strictRootUtf8(original)
        check(bytes.size <= MAX_TRANSFER_BYTES) { "C_RECOVERY_SHARE_INVALID" }
        output.write(bytes)
        output.flush()
    }

    fun read(
        context: Context,
        uri: Uri,
    ): String {
        PallaSyncRecoveryOfflineMedia.requireLocal(uri)
        return checkNotNull(context.contentResolver.openInputStream(uri)) { "C_RECOVERY_STORAGE_UNSAFE" }.use(::read)
    }

    fun write(
        context: Context,
        uri: Uri,
        original: String,
    ) {
        PallaSyncRecoveryOfflineMedia.requireLocal(uri)
        // Encode and enforce the limit before opening/truncating the chosen file.
        val bytes = strictRootUtf8(original)
        check(bytes.size <= MAX_TRANSFER_BYTES) { "C_RECOVERY_SHARE_INVALID" }
        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_RECOVERY_STORAGE_UNSAFE" }.use {
            it.write(bytes)
            it.flush()
        }
    }

    private fun decode(bytes: ByteArray): String =
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    fun qrDescriptor(value: String): String {
        val bytes = runCatching { strictRootUtf8(value) }.getOrElse { error("C_RECOVERY_SHARE_INVALID") }
        check(bytes.isNotEmpty() && bytes.size <= MAX_TRANSFER_BYTES) { "C_RECOVERY_SHARE_INVALID" }
        bytes.fill(0)
        return value
    }
}
