package com.yunfie.illustia.data

internal object PixivApiConfig {
    const val CLIENT_ID = "MOBrBDS8blbauoSck0ZfDbtuzpyT"
    const val CLIENT_SECRET = "lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj"

    // Pixiv Android App 5.0.166 hash salt used by PixEz.
    const val CLIENT_HASH_SECRET =
        "28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c"

    const val REDIRECT_URI = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback"

    // PixEz currently reports 5.0.166.
    const val APP_VERSION = "5.0.166"
    const val APP_OS_VERSION = "10.0"
}
