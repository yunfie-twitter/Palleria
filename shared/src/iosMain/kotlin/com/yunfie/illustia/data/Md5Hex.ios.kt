package com.yunfie.illustia.data

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import kotlin.experimental.ExperimentalNativeApi

/**
 * Pixiv のリクエスト署名に使う MD5。
 *
 * `java.security.MessageDigest` の代わりに CommonCrypto を直接呼ぶ。出力は
 * 小文字 16 進 32 文字で、Android 実装と同一。
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
internal actual fun md5Hex(input: String): String {
    val bytes = input.encodeToByteArray()
    val digest = UByteArray(CC_MD5_DIGEST_LENGTH.toInt())

    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            digest.usePinned { digestPinned ->
                CC_MD5(pinned.addressOf(0), bytes.size.toUInt(), digestPinned.addressOf(0))
            }
        }
    } else {
        // CC_MD5 requires a valid pointer even for empty input.
        UByteArray(1).usePinned { empty ->
            digest.usePinned { digestPinned ->
                CC_MD5(empty.addressOf(0), 0u, digestPinned.addressOf(0))
            }
        }
    }

    return buildString(digest.size * 2) {
        for (byte in digest) {
            val value = byte.toInt()
            append(HEX_DIGITS[value shr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }
}

private const val HEX_DIGITS = "0123456789abcdef"
