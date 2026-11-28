package com.yunfie.illustia.ui.screens

/** Shizuku 経由インストールの現在の状態。 */
data class ShizukuInstallState(
    val available: Boolean = false,
    val permissionGranted: Boolean = false,
)

/** アップデート設定画面が要求する操作。 */
interface UpdateSettingsActions {
    fun updateInstallMethod(value: String)

    fun updateIncludePrereleaseUpdates(enabled: Boolean)

    fun updateNotifyNewVersion(enabled: Boolean)

    fun updateCheckUpdatesInBackground(enabled: Boolean)

    fun updateAutoDownloadUpdates(enabled: Boolean)

    fun updateAutoDownloadWifiOnly(enabled: Boolean)

    fun updateAutoCheckUpdateOnStartup(enabled: Boolean)

    fun requestShizukuPermission()

    fun openShizukuApp()

    fun installMethodOptions(): List<String>
}
