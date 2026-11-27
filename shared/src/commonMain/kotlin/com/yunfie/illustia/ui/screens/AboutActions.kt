package com.yunfie.illustia.ui.screens

import com.yunfie.illustia.updater.AppReleaseInfo

/** アプリについて画面が要求する操作とプラットフォーム情報。 */
interface AboutActions {
    fun checkForUpdates(silent: Boolean)

    fun downloadUpdate(release: AppReleaseInfo)

    fun cancelDownloadUpdate()

    fun installUpdate(apkPath: String)

    /** 表示用のアプリバージョン。 */
    fun appVersion(): String?

    /** 同梱の第三者ライセンス一覧 (licenses.json) を読む。 */
    fun readThirdPartyLicenses(): List<ThirdPartyLicenseItem>
}

/** 第三者ライセンス 1 件分。 */
data class ThirdPartyLicenseItem(
    val name: String,
    val category: String,
    val version: String,
    val author: String,
    val license: String,
    val url: String,
)
