package com.yunfie.illustia.pallasync

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** §§7.11.1/18.6–18.7: the original signed status array, without packages or an invented wire wrapper. */
internal object PallaSyncRecoveryPublicStatusMedia {
    private const val MAX_BYTES = 4_194_304
    private const val CHUNK_BYTES = 8192

    fun read(
        input: InputStream,
        expected: PallaSyncRecoverySetStore.Entry,
    ): String {
        val bytes = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_BYTES)
        while (true) {
            var count = input.read(chunk)
            if (count == 0) {
                val single = input.read()
                if (single < 0) {
                    count = -1
                } else {
                    chunk[0] = single.toByte()
                    count = 1
                }
            }
            if (count < 0) break
            check(count <= MAX_BYTES - bytes.size()) { "C_RECOVERY_SHARE_INVALID" }
            bytes.write(chunk, 0, count)
        }
        val decoder =
            Charsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        // The store compares ancestry and retains verified fork evidence; parsing must not discard that evidence.
        return PallaSyncCore.validateRecoverySetState(expected.manifestJson, text, expected.admin, null)
    }

    fun write(
        output: OutputStream,
        expected: PallaSyncRecoverySetStore.Entry,
    ) {
        output.write(checkedBytes(expected))
        output.flush()
    }

    fun read(
        context: Context,
        uri: Uri,
        expected: PallaSyncRecoverySetStore.Entry,
    ): String {
        PallaSyncRecoveryOfflineMedia.requireLocal(uri)
        return checkNotNull(context.contentResolver.openInputStream(uri)) { "C_RECOVERY_STORAGE_UNSAFE" }.use { read(it, expected) }
    }

    fun write(
        context: Context,
        uri: Uri,
        expected: PallaSyncRecoverySetStore.Entry,
    ) {
        PallaSyncRecoveryOfflineMedia.requireLocal(uri)
        val bytes = checkedBytes(expected)
        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_RECOVERY_STORAGE_UNSAFE" }.use {
            it.write(bytes)
            it.flush()
        }
    }

    private fun checkedBytes(expected: PallaSyncRecoverySetStore.Entry): ByteArray {
        val canonical = PallaSyncCore.validateRecoverySetState(expected.manifestJson, expected.statusesJson, expected.admin, expected.head)
        val bytes = canonical.toByteArray(Charsets.UTF_8)
        check(bytes.size <= MAX_BYTES) { "C_RECOVERY_SHARE_INVALID" }
        return bytes
    }
}
