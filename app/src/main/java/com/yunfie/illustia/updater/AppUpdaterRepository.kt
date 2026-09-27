package com.yunfie.illustia.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.resume

private const val HTTP_NOT_FOUND = 404
private const val HTTP_NOT_MODIFIED = 304
private const val HTTP_FORBIDDEN = 403
private const val SHIZUKU_MIN_API_VERSION = 11
private const val SHIZUKU_DEFAULT_REQUEST_CODE = 1001
private const val THREAD_JOIN_TIMEOUT_MS = 5000L
private const val PROGRESS_THROTTLE_INTERVAL_MS = 100L
private const val PROGRESS_THROTTLE_DELTA = 0.01f

@Suppress("TooManyFunctions")
class AppUpdaterRepository(
    private val context: Context,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val updatesDir: File
        get() = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }

    private var activeDownloadCall: okhttp3.Call? = null
    private val downloadLock = Any()

    fun getCurrentVersionName(): String =
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName.orEmpty()
        }.getOrNull()?.ifBlank { "5.5.24" } ?: "5.5.24"

    fun cancelDownload() {
        synchronized(downloadLock) {
            activeDownloadCall?.cancel()
            activeDownloadCall = null
        }
    }

    suspend fun fetchLatestRelease(includePrerelease: Boolean = false): Result<AppReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url =
                    if (includePrerelease) {
                        "https://api.github.com/repos/yunfie-twitter/Palleria/releases?per_page=10"
                    } else {
                        "https://api.github.com/repos/yunfie-twitter/Palleria/releases/latest"
                    }
                val prefs = context.getSharedPreferences("app_updater_cache", Context.MODE_PRIVATE)
                val etagKey = if (includePrerelease) "etag_prerelease" else "etag_latest"
                val bodyKey = if (includePrerelease) "body_prerelease" else "body_latest"
                val cachedEtag = prefs.getString(etagKey, null)

                val requestBuilder =
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "application/vnd.github+json")

                if (!cachedEtag.isNullOrBlank()) {
                    requestBuilder.header("If-None-Match", cachedEtag)
                }

                val request = requestBuilder.build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.code == HTTP_NOT_MODIFIED) {
                        val cachedBody = prefs.getString(bodyKey, null)
                        if (!cachedBody.isNullOrBlank()) {
                            return@runCatching parseResponseBody(cachedBody)
                        }
                    }

                    if (!response.isSuccessful) {
                        val body = response.body.string()
                        if (response.code == HTTP_NOT_FOUND) return@runCatching null
                        if (response.code == HTTP_FORBIDDEN) {
                            val cachedBody = prefs.getString(bodyKey, null)
                            if (!cachedBody.isNullOrBlank()) {
                                return@runCatching parseResponseBody(cachedBody)
                            }
                        }
                        throw IllegalStateException("GitHub API error (${response.code}): $body")
                    }

                    val bodyString = response.body.string()
                    val newEtag = response.header("ETag")
                    prefs
                        .edit()
                        .apply {
                            if (!newEtag.isNullOrBlank()) putString(etagKey, newEtag)
                            putString(bodyKey, bodyString)
                        }.apply()

                    parseResponseBody(bodyString)
                }
            }
        }

    private fun parseResponseBody(bodyString: String): AppReleaseInfo? {
        val element = json.parseToJsonElement(bodyString)
        return when (element) {
            is JsonArray -> {
                element.firstNotNullOfOrNull { item ->
                    val obj = runCatching { item.jsonObject }.getOrNull() ?: return@firstNotNullOfOrNull null
                    parseReleaseObject(obj)
                }
            }

            is JsonObject -> {
                parseReleaseObject(element)
            }

            else -> {
                null
            }
        }
    }

    private fun parseReleaseObject(jsonObject: JsonObject): AppReleaseInfo? {
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
        val isPrerelease = jsonObject["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false

        val assets = jsonObject["assets"]?.let { it as? JsonArray } ?: JsonArray(emptyList())
        val apkAssets =
            assets.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.filter {
                val name = it["name"]?.jsonPrimitive?.content.orEmpty()
                name.endsWith(".apk", ignoreCase = true)
            }
        val chosenAsset = selectBestApkAsset(apkAssets)
        val apkUrl =
            chosenAsset
                ?.get("browser_download_url")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
        val apkName =
            chosenAsset
                ?.get("name")
                ?.jsonPrimitive
                ?.content
                .orEmpty()
        val apkSize = chosenAsset?.get("size")?.jsonPrimitive?.longOrNull ?: 0L
        val sha256Checksum = chosenAsset?.let { extractSha256(it, body, apkName.orEmpty()) }

        if (versionName.isBlank() || apkUrl.isNullOrBlank()) {
            return null
        }

        return AppReleaseInfo(
            versionName = versionName,
            releaseTitle = title,
            releaseNotes = body,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl,
            apkFileName = apkName.ifBlank { "Palleria-$versionName.apk" },
            apkSize = apkSize,
            sha256Checksum = sha256Checksum,
            isPrerelease = isPrerelease,
        )
    }

    fun isNewerVersion(
        remoteVersion: String,
        currentVersion: String = getCurrentVersionName(),
    ): Boolean = compareVersions(remoteVersion, currentVersion) > 0

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    suspend fun downloadApk(
        release: AppReleaseInfo,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitizedFileName = File(release.apkFileName).name.ifBlank { "Palleria-update.apk" }
                val targetFile = File(updatesDir, sanitizedFileName)
                if (targetFile.exists() && targetFile.length() == release.apkSize && release.apkSize > 0) {
                    if (verifyFileChecksum(targetFile, release.sha256Checksum)) {
                        onProgress(1.0f, release.apkSize, release.apkSize)
                        return@runCatching targetFile
                    } else {
                        targetFile.delete()
                    }
                }

                val request = Request.Builder().url(release.apkDownloadUrl).build()
                val call = httpClient.newCall(request)
                synchronized(downloadLock) {
                    activeDownloadCall = call
                }
                try {
                    call.execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Download failed (${response.code})")
                        }
                        val body = response.body
                        val totalBytes = if (release.apkSize > 0) release.apkSize else body.contentLength()
                        val tempFile = File(updatesDir, "$sanitizedFileName.tmp")
                        val messageDigest = MessageDigest.getInstance("SHA-256")
                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                val buffer = ByteArray(8192)
                                var readBytes: Int
                                var totalRead = 0L
                                var lastProgressTime = 0L
                                var lastReportedProgress = -1f
                                while (input.read(buffer).also { readBytes = it } != -1) {
                                    output.write(buffer, 0, readBytes)
                                    messageDigest.update(buffer, 0, readBytes)
                                    totalRead += readBytes
                                    val progress = if (totalBytes > 0) (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                                    val now = System.currentTimeMillis()
                                    if (
                                        now - lastProgressTime >= PROGRESS_THROTTLE_INTERVAL_MS ||
                                        progress - lastReportedProgress >= PROGRESS_THROTTLE_DELTA ||
                                        totalRead == totalBytes
                                    ) {
                                        lastProgressTime = now
                                        lastReportedProgress = progress
                                        onProgress(progress, totalRead, totalBytes)
                                    }
                                }
                                output.flush()
                            }
                        }

                        val computedSha256 = messageDigest.digest().joinToString("") { "%02x".format(it) }
                        val expectedSha256 = release.sha256Checksum?.trim()?.lowercase()
                        if (!expectedSha256.isNullOrBlank() && !computedSha256.equals(expectedSha256, ignoreCase = true)) {
                            if (tempFile.exists()) tempFile.delete()
                            throw SecurityException(
                                "APK checksum verification failed. Expected: $expectedSha256, Calculated: $computedSha256",
                            )
                        }

                        if (targetFile.exists()) targetFile.delete()
                        if (!tempFile.renameTo(targetFile)) {
                            tempFile.copyTo(targetFile, overwrite = true)
                            tempFile.delete()
                        }
                        targetFile
                    }
                } finally {
                    synchronized(downloadLock) {
                        if (activeDownloadCall === call) {
                            activeDownloadCall = null
                        }
                    }
                }
            }
        }

    fun calculateFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("ReturnCount")
    fun verifyFileChecksum(
        file: File,
        expectedSha256: String?,
    ): Boolean {
        if (expectedSha256.isNullOrBlank()) return true
        if (!file.exists() || !file.isFile || file.length() == 0L) return false
        val actual = runCatching { calculateFileSha256(file) }.getOrNull() ?: return false
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }

    fun isShizukuAvailable(): Boolean =
        runCatching {
            Shizuku.pingBinder()
        }.getOrDefault(false)

    fun isShizukuPermissionGranted(): Boolean =
        runCatching {
            if (!isShizukuAvailable()) return false
            if (Shizuku.getVersion() < SHIZUKU_MIN_API_VERSION) {
                context.checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        }.getOrDefault(false)

    fun requestShizukuPermission(requestCode: Int = SHIZUKU_DEFAULT_REQUEST_CODE) {
        runCatching {
            if (isShizukuAvailable()) {
                if (!isShizukuPermissionGranted()) {
                    if (Shizuku.getVersion() >= SHIZUKU_MIN_API_VERSION) {
                        Shizuku.requestPermission(requestCode)
                    }
                }
            } else {
                val listener =
                    object : Shizuku.OnBinderReceivedListener {
                        override fun onBinderReceived() {
                            runCatching {
                                Shizuku.removeBinderReceivedListener(this)
                                if (!isShizukuPermissionGranted() && Shizuku.getVersion() >= SHIZUKU_MIN_API_VERSION) {
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
            if (Shizuku.getVersion() < SHIZUKU_MIN_API_VERSION) return@withContext false

            runCatching {
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
            }.getOrDefault(false)
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
                            val shizukuSuccess = runCatching { installViaShizuku(apkFile) }.isSuccess
                            if (!shizukuSuccess) {
                                installViaStandardIntent(apkFile)
                            }
                        } else {
                            installViaStandardIntent(apkFile)
                        }
                    }
                }
            }
        }

    internal fun validateApkFile(apkFile: File) {
        val canonicalApk = apkFile.canonicalFile
        val canonicalUpdates = updatesDir.canonicalFile
        require(canonicalApk.path.startsWith(canonicalUpdates.path + File.separator)) {
            "Unauthorized APK file location: ${apkFile.absolutePath}"
        }
        require(canonicalApk.exists() && canonicalApk.isFile && canonicalApk.length() > 0) {
            "Valid APK file required: ${apkFile.absolutePath}"
        }
        val archiveInfo = context.packageManager.getPackageArchiveInfo(canonicalApk.absolutePath, 0)
        require(archiveInfo != null && archiveInfo.packageName == context.packageName) {
            "APK package (${archiveInfo?.packageName}) does not match app (${context.packageName})"
        }
    }

    private fun installViaStandardIntent(apkFile: File) {
        validateApkFile(apkFile)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        context.startActivity(intent)
    }

    private fun installViaShizuku(apkFile: File) {
        validateApkFile(apkFile)
        val fileSize = apkFile.length()

        val sessionId = createInstallSession(fileSize)
        var committed = false
        try {
            writeApkToSession(sessionId, fileSize, apkFile)
            commitInstallSession(sessionId)
            committed = true
        } finally {
            if (!committed) {
                abandonInstallSession(sessionId)
            }
        }
    }

    private fun createInstallSession(fileSize: Long): Int {
        // Attempt session creation with installer package attribution first
        val primaryCommand =
            arrayOf(
                "pm",
                "install-create",
                "-r",
                "-d",
                "-t",
                "-i",
                context.packageName,
                "-S",
                fileSize.toString(),
            )
        val (exitCode, output) = runShizukuCommand(primaryCommand)
        val parsedId = extractSessionId(output)
        if (exitCode == 0 && parsedId != null && !output.contains("Failure", ignoreCase = true)) {
            return parsedId
        }

        // Fallback without -i flag if restricted on vendor ROMs
        val fallbackCommand =
            arrayOf(
                "pm",
                "install-create",
                "-r",
                "-d",
                "-t",
                "-S",
                fileSize.toString(),
            )
        val (fallbackExitCode, fallbackOutput) = runShizukuCommand(fallbackCommand)
        val fallbackId = extractSessionId(fallbackOutput)
        if (fallbackExitCode == 0 && fallbackId != null && !fallbackOutput.contains("Failure", ignoreCase = true)) {
            return fallbackId
        }

        val errorDetails = fallbackOutput.ifBlank { output }.ifBlank { "Exit code $fallbackExitCode" }
        throw IllegalStateException("Failed to create PackageInstaller session via Shizuku ($fallbackExitCode): $errorDetails")
    }

    private fun writeApkToSession(
        sessionId: Int,
        fileSize: Long,
        apkFile: File,
    ) {
        val command =
            arrayOf(
                "pm",
                "install-write",
                "-S",
                fileSize.toString(),
                sessionId.toString(),
                "base.apk",
                "-",
            )
        val process = createShizukuProcess(command)

        var stdout = ""
        var stderr = ""
        val stdoutThread =
            Thread {
                stdout = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("")
            }.apply { start() }
        val stderrThread =
            Thread {
                stderr = runCatching { process.errorStream.bufferedReader().readText() }.getOrDefault("")
            }.apply { start() }

        streamApkToProcess(process, apkFile)

        val exitCode = process.waitFor()
        runCatching { stdoutThread.join(THREAD_JOIN_TIMEOUT_MS) }
        runCatching { stderrThread.join(THREAD_JOIN_TIMEOUT_MS) }

        val combinedOutput = "$stdout\n$stderr".trim()
        val hasFailure =
            combinedOutput.contains("Failure", ignoreCase = true) ||
                combinedOutput.contains("Error:", ignoreCase = true)

        if (exitCode != 0 || hasFailure) {
            val errorDetails = combinedOutput.ifBlank { "Exit code $exitCode" }
            throw IllegalStateException("Failed to stream APK to session $sessionId: $errorDetails")
        }
    }

    private fun commitInstallSession(sessionId: Int) {
        val command = arrayOf("pm", "install-commit", sessionId.toString())
        val (exitCode, output) = runShizukuCommand(command)
        val hasFailure =
            output.contains("Failure", ignoreCase = true) ||
                output.contains("Error:", ignoreCase = true)
        val hasSuccess = output.contains("Success", ignoreCase = true)

        if (exitCode != 0 || hasFailure || !hasSuccess) {
            val errorDetails = output.ifBlank { "Exit code $exitCode" }
            throw IllegalStateException("Failed to commit Shizuku session $sessionId: $errorDetails")
        }
    }

    private fun abandonInstallSession(sessionId: Int) {
        runCatching {
            val command = arrayOf("pm", "install-abandon", sessionId.toString())
            runShizukuCommand(command)
        }
    }

    private fun runShizukuCommand(command: Array<String>): Pair<Int, String> {
        val process = createShizukuProcess(command)
        var stdout = ""
        var stderr = ""

        val stdoutThread =
            Thread {
                stdout = runCatching { process.inputStream.bufferedReader().readText() }.getOrDefault("")
            }.apply { start() }

        val stderrThread =
            Thread {
                stderr = runCatching { process.errorStream.bufferedReader().readText() }.getOrDefault("")
            }.apply { start() }

        val exitCode = process.waitFor()
        runCatching { stdoutThread.join(THREAD_JOIN_TIMEOUT_MS) }
        runCatching { stderrThread.join(THREAD_JOIN_TIMEOUT_MS) }

        return Pair(exitCode, "$stdout\n$stderr".trim())
    }

    private fun createShizukuProcess(command: Array<String>): Process {
        val newProcessMethod =
            Shizuku::class.java
                .getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java,
                ).apply { isAccessible = true }

        return runCatching {
            newProcessMethod.invoke(null, command, null, null) as Process
        }.getOrElse { error ->
            throw IllegalStateException("Failed to start Shizuku process: ${error.message}", error)
        }
    }

    private fun streamApkToProcess(
        process: Process,
        apkFile: File,
    ) {
        try {
            process.outputStream.use { outputStream ->
                apkFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream, bufferSize = 65536)
                }
                outputStream.flush()
            }
        } catch (e: IOException) {
            runCatching { process.destroy() }
            throw IllegalStateException("Failed to stream APK to Shizuku installer: ${e.message}", e)
        }
    }

    companion object {
        private val SESSION_ID_REGEX = Regex("""\[(\d+)\]|session\s+(\d+)""", RegexOption.IGNORE_CASE)
        private val SHA256_REGEX = Regex("^[a-fA-F0-9]{64}$")

        @Suppress("ReturnCount")
        fun extractSha256(
            assetObj: JsonObject,
            releaseBody: String = "",
            apkName: String = "",
        ): String? {
            val digest =
                assetObj["digest"]
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty()
                    .trim()
            if (digest.isNotBlank()) {
                val candidate = if (digest.contains(':')) digest.substringAfter(':').trim() else digest
                if (candidate.matches(SHA256_REGEX)) {
                    return candidate.lowercase()
                }
            }

            if (apkName.isNotBlank() && releaseBody.isNotBlank()) {
                val patternFileSpecific =
                    Regex("""([a-fA-F0-9]{64})\s+[*]?${Regex.escape(apkName)}""", RegexOption.IGNORE_CASE)
                patternFileSpecific.find(releaseBody)?.groupValues?.get(1)?.let {
                    return it.lowercase()
                }

                val patternNameFirst =
                    Regex("""${Regex.escape(apkName)}\s*[:=\s]\s*([a-fA-F0-9]{64})""", RegexOption.IGNORE_CASE)
                patternNameFirst.find(releaseBody)?.groupValues?.get(1)?.let {
                    return it.lowercase()
                }
            }

            if (releaseBody.isNotBlank()) {
                val patternLabeled = Regex("""(?:sha256|SHA256)\s*[:=]?\s*([a-fA-F0-9]{64})""")
                patternLabeled.find(releaseBody)?.groupValues?.get(1)?.let {
                    return it.lowercase()
                }
            }

            return null
        }

        @Suppress("ReturnCount")
        fun selectBestApkAsset(
            apkAssets: List<JsonObject>,
            supportedAbis: Array<String> = Build.SUPPORTED_ABIS,
        ): JsonObject? {
            if (apkAssets.isEmpty()) return null
            if (apkAssets.size == 1) return apkAssets.first()

            for (abi in supportedAbis) {
                val normalizedAbi = abi.lowercase()
                val match =
                    apkAssets.firstOrNull { asset ->
                        val name =
                            asset["name"]
                                ?.jsonPrimitive
                                ?.content
                                .orEmpty()
                                .lowercase()
                        name.contains(normalizedAbi)
                    }
                if (match != null) return match
            }

            val universalMatch =
                apkAssets.firstOrNull { asset ->
                    val name =
                        asset["name"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                            .lowercase()
                    name.contains("universal")
                }
            if (universalMatch != null) return universalMatch

            return apkAssets.first()
        }

        fun extractSessionId(output: String): Int? {
            val match = SESSION_ID_REGEX.find(output)
            val idString = match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
            return idString?.toIntOrNull()
        }

        @Suppress("ReturnCount", "CyclomaticComplexMethod")
        fun compareVersions(
            v1: String,
            v2: String,
        ): Int {
            if (v1 == v2) return 0
            val core1 = v1.substringBefore("-").trim()
            val core2 = v2.substringBefore("-").trim()

            val parts1 = core1.split(".").mapNotNull { it.toIntOrNull() }
            val parts2 = core2.split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(parts1.size, parts2.size)
            for (i in 0 until maxLen) {
                val num1 = parts1.getOrElse(i) { 0 }
                val num2 = parts2.getOrElse(i) { 0 }
                if (num1 != num2) return num1.compareTo(num2)
            }

            val hasPre1 = v1.contains("-")
            val hasPre2 = v2.contains("-")
            if (!hasPre1 && hasPre2) return 1
            if (hasPre1 && !hasPre2) return -1
            if (!hasPre1 && !hasPre2) return 0

            val pre1 = v1.substringAfter("-").trim()
            val pre2 = v2.substringAfter("-").trim()
            val segs1 = pre1.split(".")
            val segs2 = pre2.split(".")
            val maxPreLen = maxOf(segs1.size, segs2.size)
            for (i in 0 until maxPreLen) {
                val seg1 = segs1.getOrNull(i)
                val seg2 = segs2.getOrNull(i)
                if (seg1 == null) return -1
                if (seg2 == null) return 1
                val int1 = seg1.toIntOrNull()
                val int2 = seg2.toIntOrNull()
                if (int1 != null && int2 != null) {
                    if (int1 != int2) return int1.compareTo(int2)
                } else if (int1 != null) {
                    return -1
                } else if (int2 != null) {
                    return 1
                } else {
                    val comp = seg1.compareTo(seg2)
                    if (comp != 0) return comp.compareTo(0)
                }
            }
            return 0
        }
    }
}
