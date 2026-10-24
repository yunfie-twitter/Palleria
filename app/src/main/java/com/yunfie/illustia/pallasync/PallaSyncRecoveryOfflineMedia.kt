package com.yunfie.illustia.pallasync

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** §7.11.4 designated local media only; device-holder backup and hosted providers are refused. */
internal object PallaSyncRecoveryOfflineMedia {
    private const val MAX_PORTABLE_BYTES = 5_592_413
    private const val BUFFER_BYTES = 8192
    private val localProviders = setOf("com.android.externalstorage.documents", "com.android.providers.downloads.documents")

    fun read(
        input: InputStream,
        trustedAdmin: String,
        knownHead: String?,
    ): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        var count = readChunk(input, buffer)
        while (count >= 0) {
            check(count <= MAX_PORTABLE_BYTES - output.size()) { "C_RECOVERY_SHARE_INVALID" }
            output.write(buffer, 0, count)
            count = readChunk(input, buffer)
        }
        return offline(PallaSyncCore.decodeRecoveryPortable(output.toByteArray(), trustedAdmin, knownHead), trustedAdmin, knownHead)
    }

    fun write(
        output: OutputStream,
        packageJson: String,
        trustedAdmin: String,
        knownHead: String?,
    ) {
        val checked = offline(packageJson, trustedAdmin, knownHead)
        output.write(PallaSyncCore.encodeRecoveryPortable(checked, trustedAdmin, knownHead))
        output.flush()
    }

    fun read(
        context: Context,
        uri: Uri,
        trustedAdmin: String,
        knownHead: String?,
    ): String {
        requireLocal(uri)
        return checkNotNull(context.contentResolver.openInputStream(uri)) { "C_RECOVERY_STORAGE_UNSAFE" }.use {
            read(it, trustedAdmin, knownHead)
        }
    }

    fun write(
        context: Context,
        uri: Uri,
        packageJson: String,
        trustedAdmin: String,
        knownHead: String?,
    ) {
        requireLocal(uri)
        val checked = offline(packageJson, trustedAdmin, knownHead)
        checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "C_RECOVERY_STORAGE_UNSAFE" }.use {
            write(it, checked, trustedAdmin, knownHead)
        }
    }

    private fun offline(
        packageJson: String,
        trustedAdmin: String,
        knownHead: String?,
    ): String {
        val canonical = PallaSyncCore.validateRecoveryPackage(packageJson, trustedAdmin, knownHead)
        val artifact = Json.parseToJsonElement(canonical).jsonObject
        check(
            artifact
                .getValue("record")
                .jsonObject
                .getValue("holder_kind")
                .jsonPrimitive.content == "offline" &&
                artifact.getValue("offline_key_envelope").jsonObject.isNotEmpty(),
        ) { "C_RECOVERY_STORAGE_UNSAFE" }
        return canonical
    }

    internal fun requireLocal(uri: Uri) {
        check(uri.scheme == "content" && uri.authority in localProviders) { "C_RECOVERY_STORAGE_UNSAFE" }
    }

    private fun readChunk(
        input: InputStream,
        buffer: ByteArray,
    ): Int {
        val count = input.read(buffer)
        if (count != 0) return count
        val next = input.read()
        return if (next < 0) {
            -1
        } else {
            buffer[0] = next.toByte()
            1
        }
    }
}
