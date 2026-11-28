package com.yunfie.illustia.platform

/** PKCE の code verifier などに使う URL-safe なランダム文字列。 */
expect fun randomUrlSafeString(byteCount: Int): String

/** PKCE の code challenge（S256）を作る。 */
expect fun String.sha256Base64Url(): String

/** SHA-256 の小文字 16 進表現。アカウント識別ハッシュに使う。 */
expect fun String.sha256Hex(): String
