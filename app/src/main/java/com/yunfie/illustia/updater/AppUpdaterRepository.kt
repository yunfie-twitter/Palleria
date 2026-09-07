package com.yunfie.illustia.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

class AppUpdaterRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val updatesDir: File
        get() = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }

    fun getCurrentVersionName(): String =
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName.orEmpty()
        }.getOrNull()?.ifBlank { "5.5.24" } ?: "5.5.24"

    suspend fun fetchLatestRelease(): Result<AppReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request
                        .Builder()
                        .url("https://api.github.com/repos/yunfie-twitter/Palleria/releases/latest")
                        .header("Accept", "application/vnd.github+json")
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body.string()
                        if (response.code == 404) return@runCatching null
                        throw IllegalStateException("GitHub API error (): ")
                    }
                    val bodyString = response.body.string()
                    val jsonObject = json.parseToJsonElement(bodyString).jsonObject
                    val tagName = jsonObject["tag_name"]?.jsonPrimitive?.content.orEmpty()
                    val versionName = tagName.removePrefix("v").trim()
                    val title =
                        jsonObject["name"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                            .ifBlank { tagName }
                    val body = jsonObject["body"]?.jsonPrimitive?.content.orEmpty()
                    val publishedAt = jsonObject["published_at"]?.jsonPrimitive?.content.orEmpty()
                    val htmlUrl = jsonObject["html_url"]?.jsonPrimitive?.content.orEmpty()

                    val assets = jsonObject["assets"]?.let { it as? JsonArray } ?: JsonArray(emptyList())
                    var apkUrl = ""
                    var apkName = ""
                    var apkSize = 0L

                    for (asset in assets) {
                        val assetObj = asset.jsonObject
                        val name = assetObj["name"]?.jsonPrimitive?.content.orEmpty()
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content.orEmpty()
                            apkName = name
                            apkSize = assetObj["size"]?.jsonPrimitive?.longOrNull ?: 0L
                            break
                        }
                    }

                    if (versionName.isBlank() || apkUrl.isBlank()) {
                        return@runCatching null
                    }

                    AppReleaseInfo(
                        versionName = versionName,
                        releaseTitle = title,
                        releaseNotes = body,
                        publishedAt = publishedAt,
                        htmlUrl = htmlUrl,
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkName.ifBlank { "Palleria-.apk" },
                        apkSize = apkSize,
                    )
                }
            }
        }

    fun isNewerVersion(
        remoteVersion: String,
        currentVersion: String = getCurrentVersionName(),
    ): Boolean = compareVersions(remoteVersion, currentVersion) > 0

    suspend fun downloadApk(
        release: AppReleaseInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetFile = File(updatesDir, release.apkFileName)
                if (targetFile.exists() && targetFile.length() == release.apkSize && release.apkSize > 0) {
                    onProgress(1.0f, release.apkSize, release.apkSize)
                    return@runCatching targetFile
                }

                val request = Request.Builder().url(release.apkDownloadUrl).build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Download failed ()")
                    }
                    val body = response.body
                    val totalBytes = if (release.apkSize > 0) release.apkSize else body.contentLength()
                    val tempFile = File(updatesDir, ".tmp")
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var readBytes: Int
                            var totalRead = 0L
                            while (input.read(buffer).also { readBytes = it } != -1) {
                                output.write(buffer, 0, readBytes)
                                totalRead += readBytes
                                val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes.toFloat() else 0f
                                onProgress(progress.coerceIn(0f, 1f), totalRead, totalBytes)
                            }
                            output.flush()
                        }
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    targetFile
                }
            }
        }

    fun isShizukuAvailable(): Boolean =
        runCatching {
            Shizuku.pingBinder()
        }.getOrDefault(false)

    fun isShizukuPermissionGranted(): Boolean =
        runCatching {
            if (!isShizukuAvailable()) return false
            if (Shizuku.getVersion() < 11) {
                context.checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        }.getOrDefault(false)

    fun requestShizukuPermission(requestCode: Int = 1001) {
        runCatching {
            if (isShizukuAvailable()) {
                if (!isShizukuPermissionGranted()) {
                    if (Shizuku.getVersion() >= 11) {
                        Shizuku.requestPermission(requestCode)
                    }
                }
            } else {
                val listener =
                    object : Shizuku.OnBinderReceivedListener {
                        override fun onBinderReceived() {
                            runCatching {
                                Shizuku.removeBinderReceivedListener(this)
                                if (!isShizukuPermissionGranted() && Shizuku.getVersion() >= 11) {
                                    Shizuku.requestPermission(requestCode)
                                }
                            }
                        }
                    }
                Shizuku.addBinderReceivedListenerSticky(listener)
            }
        }
    }

    suspend fun requestShizukuPermissionSuspending(requestCode: Int = 1001): Boolean =
        withContext(Dispatchers.Main) {
            if (!isShizukuAvailable()) {
                val bound =
                    withTimeoutOrNull(2000L) {
                        suspendCancellableCoroutine<Boolean> { continuation ->
                            val listener =
                                object : Shizuku.OnBinderReceivedListener {
                                    override fun onBinderReceived() {
                                        runCatching { Shizuku.removeBinderReceivedListener(this) }
                                        if (continuation.isActive) continuation.resume(true)
                                    }
                                }
                            Shizuku.addBinderReceivedListenerSticky(listener)
                            continuation.invokeOnCancellation {
                                runCatching { Shizuku.removeBinderReceivedListener(listener) }
                            }
                        }
                    } ?: false
                if (!bound || !isShizukuAvailable()) return@withContext false
            }

            if (isShizukuPermissionGranted()) return@withContext true
            if (Shizuku.getVersion() < 11) return@withContext false

            try {
                suspendCancellableCoroutine { continuation ->
                    val listener =
                        object : Shizuku.OnRequestPermissionResultListener {
                            override fun onRequestPermissionResult(
                                reqCode: Int,
                                grantResult: Int,
                            ) {
                                if (reqCode == requestCode) {
                                    runCatching { Shizuku.removeRequestPermissionResultListener(this) }
                                    if (continuation.isActive) {
                                        continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                                    }
                                }
                            }
                        }
                    Shizuku.addRequestPermissionResultListener(listener)
                    continuation.invokeOnCancellation {
                        runCatching { Shizuku.removeRequestPermissionResultListener(listener) }
                    }
                    Shizuku.requestPermission(requestCode)
                }
            } catch (e: Exception) {
                false
            }
        }

    suspend fun installApk(
        apkFile: File,
        method: UpdateInstallMethod,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (method) {
                    UpdateInstallMethod.STANDARD_APK -> {
                        installViaStandardIntent(apkFile)
                    }

                    UpdateInstallMethod.SHIZUKU -> {
                        val hasPermission =
                            if (isShizukuPermissionGranted()) {
                                true
                            } else {
                                requestShizukuPermissionSuspending()
                            }

                        if (hasPermission) {
                            installViaShizuku(apkFile)
                        } else if (isShizukuAvailable()) {
                            throw IllegalStateException("Shizuku permission was not granted")
                        } else {
                            installViaStandardIntent(apkFile)
                        }
                    }
                }
            }
        }

    private fun installViaStandardIntent(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }

    private fun installViaShizuku(apkFile: File) {
        require(apkFile.exists() && apkFile.isFile) { "APK file does not exist: ${apkFile.absolutePath}" }
        val fileSize = apkFile.length()
        require(fileSize > 0) { "APK file is empty: ${apkFile.absolutePath}" }

        // Use streaming installation with -S <fileSize> via standard input.
        // Direct file paths cannot be read by shell UID (2000) due to app private sandbox permissions.
        val command = arrayOf("pm", "install", "-r", "-d", "-t", "-S", fileSize.toString())
        val newProcessMethod =
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        val process =
            runCatching {
                newProcessMethod.invoke(null, command, null, null) as Process
            }.getOrElse { error ->
                throw IllegalStateException("Failed to start Shizuku process: ${error.message}", error)
            }

        var stdout = ""
        var stderr = ""

        val stdoutThread =
            Thread {
                stdout =
                    runCatching {
                        process.inputStream.bufferedReader().readText()
                    }.getOrDefault("")
            }.apply { start() }

        val stderrThread =
            Thread {
                stderr =
                    runCatching {
                        process.errorStream.bufferedReader().readText()
                    }.getOrDefault("")
            }.apply { start() }

        try {
            process.outputStream.use { outputStream ->
                apkFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 65536)
                }
                outputStream.flush()
            }
        } catch (e: Exception) {
            runCatching { process.destroy() }
            throw IllegalStateException("Failed to stream APK to Shizuku installer: ${e.message}", e)
        }

        val exitCode = process.waitFor()
        runCatching { stdoutThread.join(5000) }
        runCatching { stderrThread.join(5000) }

        val combinedOutput = "$stdout\n$stderr".trim()
        val hasFailure =
            combinedOutput.contains("Failure", ignoreCase = true) ||
                combinedOutput.contains("Error:", ignoreCase = true)
        val hasSuccess = combinedOutput.contains("Success", ignoreCase = true)

        if (exitCode != 0 || hasFailure || !hasSuccess) {
            val errorDetails = combinedOutput.ifBlank { "Exit code $exitCode" }
            throw IllegalStateException("Shizuku installation failed ($exitCode): $errorDetails")
        }
    }

    companion object {
        fun compareVersions(
            v1: String,
            v2: String,
        ): Int {
            val parts1 = v1.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
            val parts2 = v2.split(".", "-", "_").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val num1 = parts1.getOrElse(i) { 0 }
                val num2 = parts2.getOrElse(i) { 0 }
                if (num1 != num2) return num1.compareTo(num2)
            }
            return 0
        }
    }
}
