package com.yunfie.illustia.platform

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

private val base64UrlEncoder = Base64.getUrlEncoder().withoutPadding()

/** PKCE の code verifier などに使う URL-safe なランダム文字列。 */
fun randomUrlSafeString(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return base64UrlEncoder.encodeToString(bytes)
}

/** PKCE の code challenge（S256）を作る。 */
fun String.sha256Base64Url(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return base64UrlEncoder.encodeToString(digest)
}
