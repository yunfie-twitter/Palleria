package com.yunfie.illustia.nativebridge

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.yunfie.illustia.platform.PlatformCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class NativeImageStore(
    private val context: Context,
) {
    private val preferences = context.getSharedPreferences("illustia", Context.MODE_PRIVATE)
    private val flutterPreferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private val savingNames = Collections.synchronizedSet(mutableSetOf<String>())

    fun save(
        input: InputStream,
        name: String,
        sourceUrl: String,
        responseMimeType: String?,
        clearOld: Boolean = false,
    ): Boolean {
        val displayName = name.withImageExtension(sourceUrl, imageMimeType(responseMimeType, sourceUrl))
        if (!savingNames.add(displayName)) return false
        return try {
            when (saveMode()) {
                SAVE_MODE_SAF -> saveToTree(input, displayName, sourceUrl, responseMimeType, clearOld)
                SAVE_MODE_DIRECT -> saveToPath(input, displayName, sourceUrl, responseMimeType, clearOld)
                else -> saveToMediaStore(input, displayName, sourceUrl, responseMimeType, clearOld)
            }
        } finally {
            savingNames.remove(displayName)
        }
    }

    fun exists(
        name: String,
        sourceUrl: String = "",
    ): Boolean {
        val displayName = if (sourceUrl.isBlank()) name else name.withImageExtension(sourceUrl, null)
        val hasDirectMatch = checkExists(displayName)
        val hasAltMatch =
            !hasDirectMatch && sourceUrl.isNotBlank() && !name.substringAfterLast('/').contains('.') &&
                SUPPORTED_EXTENSIONS.any { ext ->
                    val altName = "$name.$ext"
                    altName != displayName && checkExists(altName)
                }
        return hasDirectMatch || hasAltMatch
    }

    private fun checkExists(displayName: String): Boolean =
        when (saveMode()) {
            SAVE_MODE_SAF -> existsInTree(displayName)
            SAVE_MODE_DIRECT -> File(baseDirectPath(), displayName).exists()
            else -> existsInMediaStore(displayName)
        }

    fun currentPath(): String? =
        when (saveMode()) {
            SAVE_MODE_SAF -> {
                context.contentResolver.persistedUriPermissions
                    .firstOrNull { it.isReadPermission && it.isWritePermission }
                    ?.uri
                    ?.toString()
            }

            SAVE_MODE_DIRECT -> {
                baseDirectPath().absolutePath
            }

            else -> {
                "${Environment.DIRECTORY_PICTURES}/Palleria"
            }
        }

    fun createChooseFolderIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }

    fun persistTreeUri(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
        context.contentResolver.persistedUriPermissions
            .filter { it.uri != uri && it.isReadPermission && it.isWritePermission }
            .forEach { context.contentResolver.releasePersistableUriPermission(it.uri, flags) }
        preferences
            .edit()
            .putInt(KEY_SAVE_MODE, SAVE_MODE_SAF)
            .apply()
    }

    fun persistReadOnlyTreeUri(uri: Uri): Boolean =
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val tree = DocumentFile.fromTreeUri(context, uri)
            require(tree != null && tree.exists() && tree.isDirectory && tree.canRead())
            true
        }.getOrDefault(false)

    fun currentPathLabel(): String {
        if (saveMode() != SAVE_MODE_SAF) return currentPath().orEmpty()
        val uri =
            context.contentResolver.persistedUriPermissions
                .firstOrNull { it.isReadPermission && it.isWritePermission }
                ?.uri
                ?: return currentPath().orEmpty()
        val documentId =
            runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                ?: return uri.toString()
        val (volume, path) =
            documentId.split(':', limit = 2).let {
                it.first() to it.getOrElse(1) { "" }
            }
        val root =
            if (volume.equals("primary", ignoreCase = true)) {
                Environment.getExternalStorageDirectory().absolutePath
            } else {
                volume
            }
        return listOf(root, path).filter { it.isNotBlank() }.joinToString(File.separator)
    }

    fun listSavedImages(selectedFolderUri: String? = null): List<NativeSavedImage> =
        runCatching {
            if (!selectedFolderUri.isNullOrBlank()) {
                val root = DocumentFile.fromTreeUri(context, Uri.parse(selectedFolderUri))
                root?.let(::listDocumentImages).orEmpty()
            } else {
                when (saveMode()) {
                    SAVE_MODE_SAF -> writableTree()?.let(::listDocumentImages).orEmpty()
                    SAVE_MODE_DIRECT -> listFileImages(baseDirectPath())
                    else -> listMediaStoreImages()
                }
            }
        }.getOrDefault(emptyList())

    fun folderLabel(uriValue: String): String =
        runCatching {
            val uri = Uri.parse(uriValue)
            DocumentFile.fromTreeUri(context, uri)?.name
                ?: DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':')
        }.getOrDefault("")

    private fun listMediaStoreImages(): List<NativeSavedImage> {
        val base = "${Environment.DIRECTORY_PICTURES}/Palleria/"
        val projection =
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED,
            )
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        return context.contentResolver
            .query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf("$base%"),
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val modifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                buildList {
                    while (cursor.moveToNext()) {
                        val uri =
                            Uri.withAppendedPath(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                cursor.getLong(idColumn).toString(),
                            )
                        add(
                            NativeSavedImage(
                                uri = uri.toString(),
                                name = cursor.getString(nameColumn).orEmpty(),
                                modifiedAtMillis = cursor.getLong(modifiedColumn) * 1_000L,
                            ),
                        )
                    }
                }
            }.orEmpty()
    }

    private fun listDocumentImages(root: DocumentFile): List<NativeSavedImage> =
        runBlocking {
            val result = CopyOnWriteArrayList<NativeSavedImage>()

            suspend fun walk(directory: DocumentFile) {
                if (result.size >= MAX_LISTED_IMAGES) return
                val items = directory.listFiles()
                coroutineScope {
                    val files = items.filter { it.isFile && it.isSupportedImage() }
                    files.forEach { item ->
                        if (result.size < MAX_LISTED_IMAGES) {
                            result.add(
                                NativeSavedImage(
                                    uri = item.uri.toString(),
                                    name = item.name.orEmpty(),
                                    modifiedAtMillis = item.lastModified(),
                                ),
                            )
                        }
                    }
                    val dirs = items.filter { it.isDirectory }
                    dirs
                        .map {
                            async(Dispatchers.IO) {
                                walk(it)
                            }
                        }.awaitAll()
                }
            }

            walk(root)
            result.sortedByDescending(NativeSavedImage::modifiedAtMillis)
        }

    private fun listFileImages(root: File): List<NativeSavedImage> {
        if (!root.isDirectory) return emptyList()
        return root
            .walkTopDown()
            .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS }
            .take(MAX_LISTED_IMAGES)
            .map {
                NativeSavedImage(
                    uri = Uri.fromFile(it).toString(),
                    name = it.name,
                    modifiedAtMillis = it.lastModified(),
                )
            }.sortedByDescending(NativeSavedImage::modifiedAtMillis)
            .toList()
    }

    private fun DocumentFile.isSupportedImage(): Boolean {
        val extension =
            name
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase(Locale.ROOT)
                .orEmpty()
        return type?.startsWith("image/") == true || extension in SUPPORTED_EXTENSIONS
    }

    private fun saveMode(): Int {
        val nativeMode = preferences.getInt(KEY_SAVE_MODE, UNKNOWN_MODE)
        val configuredMode =
            if (nativeMode != UNKNOWN_MODE) {
                nativeMode
            } else {
                flutterPreferences.getLong("flutter.save_mode", SAVE_MODE_MEDIASTORE.toLong()).toInt()
            }
        // RELATIVE_PATH and IS_PENDING are only available with scoped storage.
        return if (!PlatformCapabilities.supportsScopedMediaStore() && configuredMode == SAVE_MODE_MEDIASTORE) {
            SAVE_MODE_DIRECT
        } else {
            configuredMode
        }
    }

    private fun saveToMediaStore(
        input: InputStream,
        displayName: String,
        sourceUrl: String,
        responseMimeType: String?,
        clearOld: Boolean,
    ): Boolean {
        val resolver = context.contentResolver
        val normalizedMimeType = imageMimeType(responseMimeType, sourceUrl)
        val relativePath = mediaRelativePath(displayName)
        if (clearOld) deleteOldMediaStoreEntry(displayName, relativePath)
        val contentValues =
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName.substringAfterLast('/'))
                put(MediaStore.MediaColumns.MIME_TYPE, normalizedMimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        val uri =
            resolver.insert(contentUriFor(displayName), contentValues)
                ?: throw IllegalStateException("MediaStoreへの登録に失敗しました。")
        runCatching {
            resolver.openOutputStream(uri, "w")?.use { output ->
                input.use { it.copyTo(output) }
            } ?: throw IllegalStateException("ファイルの書き込みに失敗しました。")
            ContentValues()
                .apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }.also { resolver.update(uri, it, null, null) }
        }.onFailure {
            resolver.delete(uri, null, null)
            throw it
        }
        return true
    }

    private fun saveToTree(
        input: InputStream,
        displayName: String,
        sourceUrl: String,
        responseMimeType: String?,
        clearOld: Boolean,
    ): Boolean {
        val tree = writableTree() ?: return false
        val names = displayName.split('/').filter { it.isNotBlank() }
        val fileName = names.lastOrNull() ?: return false
        var parent = tree
        names.dropLast(1).forEach { segment ->
            parent = parent.findFile(segment)?.takeIf { it.isDirectory } ?: parent.createDirectory(segment) ?: return false
        }
        if (clearOld) parent.findFile(fileName.replace("_p0", ""))?.delete()
        parent.findFile(fileName)?.delete()
        val target = parent.createFile(imageMimeType(responseMimeType, sourceUrl), fileName) ?: return false
        context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
            input.use { it.copyTo(output) }
        } ?: return false
        return true
    }

    private fun saveToPath(
        input: InputStream,
        displayName: String,
        sourceUrl: String,
        responseMimeType: String?,
        clearOld: Boolean,
    ): Boolean {
        val target = File(baseDirectPath(), displayName)
        target.parentFile?.mkdirs()
        if (clearOld && displayName.contains("_p0")) {
            File(baseDirectPath(), displayName.replace("_p0", "")).delete()
        }
        if (clearOld && target.exists()) {
            target.delete()
        }
        target.outputStream().use { output ->
            input.use { it.copyTo(output) }
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(imageMimeType(responseMimeType, sourceUrl)),
            null,
        )
        return true
    }

    private fun existsInMediaStore(displayName: String): Boolean {
        val relativePath = mediaRelativePath(displayName)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val args = arrayOf(relativePath.ensureTrailingSlash(), displayName.substringAfterLast('/'))
        return context.contentResolver
            .query(
                contentUriFor(displayName),
                arrayOf(MediaStore.MediaColumns._ID),
                selection,
                args,
                null,
            )?.use { it.moveToFirst() } == true
    }

    private fun existsInTree(displayName: String): Boolean {
        val tree = writableTree() ?: return false
        val names = displayName.split('/').filter { it.isNotBlank() }
        val parent =
            names.dropLast(1).fold<String, DocumentFile?>(tree) { dir, segment ->
                dir?.findFile(segment)?.takeIf { it.isDirectory }
            }
        return names.lastOrNull()?.let { fileName -> parent?.findFile(fileName)?.exists() } == true
    }

    private fun deleteOldMediaStoreEntry(
        displayName: String,
        relativePath: String,
    ) {
        val resolver = context.contentResolver
        val contentUri = contentUriFor(displayName)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val args = arrayOf(relativePath.ensureTrailingSlash(), displayName.substringAfterLast('/'))
        resolver.delete(contentUri, selection, args)
        if (displayName.contains("_p0")) {
            val oldName = displayName.replace("_p0", "").substringAfterLast('/')
            resolver.delete(contentUri, selection, arrayOf(relativePath.ensureTrailingSlash(), oldName))
        }
    }

    private fun contentUriFor(displayName: String): Uri =
        if (displayName.endsWith(".mp4", ignoreCase = true)) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    private fun writableTree(): DocumentFile? {
        val uri =
            context.contentResolver.persistedUriPermissions
                .firstOrNull { it.isReadPermission && it.isWritePermission }
                ?.uri
                ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    private fun baseDirectPath(): File {
        val path =
            preferences.getString(KEY_STORE_PATH, null)
                ?: flutterPreferences.getString("flutter.store_path", null)
                ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Palleria").absolutePath
        return File(path)
    }

    private fun mediaRelativePath(displayName: String): String {
        val folder = displayName.substringBeforeLast('/', missingDelimiterValue = "")
        return if (folder.isBlank()) {
            "${Environment.DIRECTORY_PICTURES}/Palleria"
        } else {
            "${Environment.DIRECTORY_PICTURES}/Palleria/$folder"
        }
    }

    private fun imageMimeType(
        responseMimeType: String?,
        sourceUrl: String,
    ): String {
        val type = responseMimeType?.substringBefore(";")?.lowercase(Locale.ROOT)
        if (type in SUPPORTED_MIME_TYPES) return type!!
        return when (sourceUrl.imageExtension()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            else -> "image/jpeg"
        }
    }

    private fun String.withImageExtension(
        sourceUrl: String,
        responseMimeType: String?,
    ): String {
        val current = substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        if (current in SUPPORTED_EXTENSIONS) return this
        val extension =
            when (responseMimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "video/mp4" -> "mp4"
                else -> sourceUrl.imageExtension().takeIf { it in SUPPORTED_EXTENSIONS } ?: "jpg"
            }
        return "$this.$extension"
    }

    private fun String.imageExtension(): String {
        val urlExtension =
            runCatching {
                Uri
                    .parse(this)
                    .lastPathSegment
                    ?.substringAfterLast('.', missingDelimiterValue = "")
                    ?.lowercase(Locale.ROOT)
                    .orEmpty()
            }.getOrDefault("")
        return MimeTypeMap.getFileExtensionFromUrl(this).takeIf { it.isNotBlank() } ?: urlExtension
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"

    companion object {
        private const val UNKNOWN_MODE = -1
        private const val SAVE_MODE_MEDIASTORE = 0
        private const val SAVE_MODE_SAF = 1
        private const val SAVE_MODE_DIRECT = 2
        private const val KEY_SAVE_MODE = "saveMode"
        private const val KEY_STORE_PATH = "storePath"
        private const val MAX_LISTED_IMAGES = 2_000
        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "mp4")
        private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4")
    }
}

data class NativeSavedImage(
    val uri: String,
    val name: String,
    val modifiedAtMillis: Long,
)
