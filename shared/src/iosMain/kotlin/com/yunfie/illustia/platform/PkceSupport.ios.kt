package com.yunfie.illustia.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/** Base64 URL-safe（パディング無し）へエンコードする。 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toBase64UrlNoPadding(): String {
    val data = usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.convert()) }
    val encoded = data.base64EncodedStringWithOptions(0u)
    val urlSafe = encoded.replace('+', '-').replace('/', '_')
    return urlSafe.trimEnd('=')
}

@OptIn(ExperimentalForeignApi::class)
actual fun randomUrlSafeString(byteCount: Int): String {
    val bytes = UByteArray(byteCount)
    bytes.usePinned { pinned ->
        SecRandomCopyBytes(kSecRandomDefault, byteCount.convert(), pinned.addressOf(0))
    }
    return ByteArray(byteCount) { bytes[it].toByte() }.toBase64UrlNoPadding()
}

@OptIn(ExperimentalForeignApi::class)
actual fun String.sha256Base64Url(): String {
    val input = encodeToByteArray()
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH.toInt())
    val source = UByteArray(input.size) { input[it].toUByte() }

    if (source.isNotEmpty()) {
        source.usePinned { pinned ->
            digest.usePinned { digestPinned ->
                CC_SHA256(pinned.addressOf(0), source.size.convert(), digestPinned.addressOf(0))
            }
        }
    } else {
        // CC_SHA256 requires a valid pointer even for empty input.
        UByteArray(1).usePinned { empty ->
            digest.usePinned { digestPinned ->
                CC_SHA256(empty.addressOf(0), 0u, digestPinned.addressOf(0))
            }
        }
    }

    return ByteArray(digest.size) { digest[it].toByte() }.toBase64UrlNoPadding()
}
