package com.yunfie.illustia.platform

import java.io.File
import java.io.FileOutputStream

/** `java.io.File` を使う Android 実装。 */
class AndroidFileSystem : FileSystem {
    override fun makeDirectories(path: String): Boolean = File(path).mkdirs()

    override fun list(path: String): List<String> = File(path).list()?.toList().orEmpty()

    override fun openWrite(path: String): OutputStreamHandle {
        val stream = FileOutputStream(path)
        return object : OutputStreamHandle {
            override fun write(bytes: ByteArray) = stream.write(bytes)

            override fun write(
                bytes: ByteArray,
                offset: Int,
                length: Int,
            ) = stream.write(bytes, offset, length)

            override fun close() = stream.close()
        }
    }

    override fun stat(path: String): FileEntry? {
        val file = File(path)
        if (!file.exists()) return null
        return FileEntry(name = file.name, isDirectory = file.isDirectory, size = if (file.isFile) file.length() else 0L)
    }

    override fun delete(path: String): Boolean = File(path).deleteRecursively()

    override fun createTempFile(
        prefix: String,
        suffix: String,
        directory: String,
    ): String = File.createTempFile(prefix, suffix, File(directory)).absolutePath

    override fun isDirectory(path: String): Boolean = File(path).isDirectory

    override fun copy(
        sourcePath: String,
        targetPath: String,
    ) {
        File(sourcePath).inputStream().use { input ->
            FileOutputStream(targetPath).use { output -> input.copyTo(output) }
        }
    }
}
