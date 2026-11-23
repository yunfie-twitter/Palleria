package com.yunfie.illustia.updater

/**
 * ViewModel 層がアプリ更新に対して行う操作の境界。
 *
 * ダウンロード・署名検証・インストールといった `PackageManager` 依存の処理は
 * 実装側（Android）に閉じ込め、ViewModel からはこのインターフェースだけを見せる。
 */
interface AppUpdateService {
    /** 現在のアプリのバージョン名。 */
    fun getCurrentVersionName(): String

    /** 過去の更新用 APK を削除する。 */
    fun cleanUpdateApks()

    /** 進行中の APK ダウンロードを中断する。 */
    fun cancelDownload()

    /** [remoteVersion] が [currentVersion] より新しいか。 */
    fun isNewerVersion(
        remoteVersion: String,
        currentVersion: String = getCurrentVersionName(),
    ): Boolean

    /** Shizuku 経由のインストール権限を要求する。 */
    fun requestShizukuPermission(requestCode: Int = 1001)

    /** Shizuku が利用可能か。 */
    fun isShizukuAvailable(): Boolean

    /** Shizuku のインストール権限が付与済みか。 */
    fun isShizukuPermissionGranted(): Boolean

    /** 最新リリース情報を取得する。[includePrerelease] でプレリリースも含める。 */
    suspend fun fetchLatestRelease(includePrerelease: Boolean = false): Result<AppReleaseInfo?>

    /**
     * APK をダウンロードする。
     * @return 保存先のパス（[installApk] にそのまま渡せる文字列）。
     */
    suspend fun downloadApk(
        release: AppReleaseInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<String>

    /** ダウンロード済みの APK をインストールする。 */
    suspend fun installApk(
        apkPath: String,
        method: UpdateInstallMethod,
    ): Result<Unit>
}
