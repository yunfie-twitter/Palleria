package com.yunfie.illustia.data

/**
 * Pixiv API の URL だけを許可する。API レスポンス由来の next_url へトークンを
 * 送らないための最終防衛線。ホスト・スキーム・ポート・userinfo を検証する。
 */
expect fun trustedPixivApiUrl(url: String): String

/** 401 / 無効なトークン応答を検出する。再ログインが必要かの判断に使う。 */
expect fun Throwable.isPixivAuthExpired(): Boolean

/** 一時的な接続断かどうか。リトライ判断に使う。 */
expect fun Throwable.isTransientConnectionIssue(): Boolean
