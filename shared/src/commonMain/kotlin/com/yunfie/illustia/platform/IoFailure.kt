package com.yunfie.illustia.platform

/**
 * ネットワーク／I/O 失敗の共通型。
 *
 * `java.io.IOException` / `java.net.SocketTimeoutException` は Kotlin/Native に無いため、
 * ViewModel 層が「タイムアウトか」「I/O 失敗か」を判定できるよう、この 2 型へ集約する。
 */
sealed class IoFailure(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/** 応答が時間内に返らなかった。 */
class TimeoutFailure(
    message: String = "request timed out",
    cause: Throwable? = null,
) : IoFailure(message, cause)

/** 接続断・読み書き失敗など、タイムアウト以外の I/O 失敗。 */
class TransportFailure(
    message: String = "network i/o failed",
    cause: Throwable? = null,
) : IoFailure(message, cause)
