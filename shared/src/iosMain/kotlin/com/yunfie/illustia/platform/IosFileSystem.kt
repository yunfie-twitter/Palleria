package com.yunfie.illustia.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.getBytes
import platform.Foundation.writeToFile

/**
 * `NSFileManager` を使う iOS 実装。
 *
 * パスは常に絶対パスの文字列で受け渡す。書き込みは `NSData` を経由し、追記が必要な
 * ストリーム API は持たない（呼び出し側は `write` を 1 回ずつ呼ぶため）。
 */
@OptIn(ExperimentalForeignApi::class)
class IosFileSystem : FileSystem {
    private val manager = NSFileManager.defaultManager

    override fun makeDirectories(path: String): Boolean =
        manager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)

    override fun list(path: String): List<String> =
        (manager.contentsOfDirectoryAtPath(path, null) ?: emptyList<Any>()).map { it.toString() }

    override fun openWrite(path: String): OutputStreamHandle = AccumulatingHandle(this, path)

    override fun readText(path: String): String? {
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    override fun readBytes(path: String): ByteArray? = NSData.dataWithContentsOfFile(path)?.toByteArray()

    override fun stat(path: String): FileEntry? {
        val attributes = manager.attributesOfItemAtPath(path, error = null) ?: return null
        val isDirectory = (attributes["NSFileType"] as? String) == "NSFileTypeDirectory"
        val size = if (isDirectory) 0L else ((attributes["NSFileSize"] as? Number)?.toLong() ?: 0L)
        return FileEntry(name = path.substringAfterLast('/'), isDirectory = isDirectory, size = size)
    }

    override fun delete(path: String): Boolean = manager.removeItemAtPath(path, error = null)

    override fun createTempFile(
        prefix: String,
        suffix: String,
        directory: String,
    ): String {
        val candidate = "$directory/$prefix-${currentTimeMillis()}$suffix"
        writeBytes(candidate, ByteArray(0))
        return candidate
    }

    override fun isDirectory(path: String): Boolean = stat(path)?.isDirectory == true

    override fun copy(
        sourcePath: String,
        targetPath: String,
    ) {
        val data = NSData.dataWithContentsOfFile(sourcePath) ?: return
        data.writeToFile(targetPath, atomically = true)
    }

    internal fun writeBytes(
        path: String,
        bytes: ByteArray,
    ): Boolean = bytes.toNSData().writeToFile(path, atomically = true)
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.convert())
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val output = ByteArray(size)
    output.usePinned { pinned ->
        this.getBytes(pinned.addressOf(0), length = length)
    }
    return output
}

/** バッファして close 時に一括書き込みするハンドル。 */
private class AccumulatingHandle(
    private val fileSystem: IosFileSystem,
    private val path: String,
) : OutputStreamHandle {
    private val bytes = ArrayList<Byte>()

    override fun write(chunk: ByteArray) {
        chunk.forEach { bytes.add(it) }
    }

    override fun write(
        chunk: ByteArray,
        offset: Int,
        length: Int,
    ) {
        for (index in offset until offset + length) bytes.add(chunk[index])
    }

    override fun close() {
        fileSystem.writeBytes(path, bytes.toByteArray())
    }
}
