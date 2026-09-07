package com.yunfie.illustia.updater

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class UpdateInstallMethod(
    val value: String,
) {
    STANDARD_APK("standard_apk"),
    SHIZUKU("shizuku"),
    ;

    companion object {
        fun fromValue(value: String?): UpdateInstallMethod =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: STANDARD_APK
    }
}

@Serializable
data class AppReleaseInfo(
    val versionName: String,
    val versionCode: Int? = null,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSize: Long,
)

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState

    data object Checking : UpdateCheckState

    data class UpdateAvailable(
        val release: AppReleaseInfo,
    ) : UpdateCheckState

    data class UpToDate(
        val currentVersion: String,
    ) : UpdateCheckState

    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateCheckState

    data class ReadyToInstall(
        val apkFile: File,
        val release: AppReleaseInfo,
    ) : UpdateCheckState

    data object Installing : UpdateCheckState

    data class Error(
        val message: String,
    ) : UpdateCheckState
}
