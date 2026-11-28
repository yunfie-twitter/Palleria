package com.yunfie.illustia.data

import com.yunfie.illustia.platform.FileSystem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.zlib.Z_FINISH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

/**
 * iOS 側の ZIP 展開。
 *
 * Kotlin/Native には ZIP を読む標準 API が無いため、中央ディレクトリを自前で走査し、
 * deflate（方式 8）は `zlib` の `inflate`、無圧縮（方式 0）はそのまま取り出す。
 * 検証は [UgoiraValidation] に委ね、ここでは「必要なファイルだけを書き出す」ことに絞る。
 */
@OptIn(ExperimentalForeignApi::class)
class IosUgoiraArchive(
    private val fileSystem: FileSystem,
) : UgoiraArchive {
    override fun extract(
        archivePath: String,
        stagingDir: String,
        requiredFiles: Set<String>,
    ) {
        val bytes =
            fileSystem.readBytes(archivePath)
                ?: throw PixivApiException(0, "ugoira archive could not be read")
        val entries = readCentralDirectory(bytes)
        val extracted = HashSet<String>()
        var count = 0
        var totalBytes = 0L
        for (entry in entries) {
            count += 1
            val name = entry.name.replace('\\', '/')
            UgoiraValidation.relativePath(name)
            UgoiraValidation.entry(count, entry.isDirectory, entry.uncompressedSize, name, requiredFiles, extracted)
            if (entry.isDirectory || name !in requiredFiles) continue
            totalBytes += entry.uncompressedSize
            UgoiraValidation.totalBytes(totalBytes)
            val data = readEntry(bytes, entry)
            fileSystem.openWrite("$stagingDir/$name").use { it.write(data) }
        }
        UgoiraValidation.complete(requiredFiles, extracted)
    }

    // --- 中央ディレクトリの走査 -------------------------------------------

    private fun readCentralDirectory(bytes: ByteArray): List<Entry> {
        val eocd = findEndOfCentralDirectory(bytes)
        val entryCount = u16(bytes, eocd + 10)
        var offset = u32(bytes, eocd + 16).toInt()
        val entries = ArrayList<Entry>(entryCount)
        repeat(entryCount) {
            if (u32(bytes, offset) != 0x02014b50L) throw PixivApiException(0, "ugoira archive has a corrupt central directory")
            val method = u16(bytes, offset + 10)
            val compressedSize = u32(bytes, offset + 20)
            val uncompressedSize = u32(bytes, offset + 24)
            val nameLength = u16(bytes, offset + 28)
            val extraLength = u16(bytes, offset + 30)
            val commentLength = u16(bytes, offset + 32)
            val localHeaderOffset = u32(bytes, offset + 42)
            val name = bytes.decodeToString(offset + 46, offset + 46 + nameLength)
            entries +=
                Entry(
                    name = name,
                    isDirectory = name.endsWith("/"),
                    compressionMethod = method,
                    compressedSize = compressedSize,
                    uncompressedSize = uncompressedSize,
                    localHeaderOffset = localHeaderOffset.toInt(),
                )
            offset += 46 + nameLength + extraLength + commentLength
        }
        return entries
    }

    private fun findEndOfCentralDirectory(bytes: ByteArray): Int {
        // EOCD は末尾 22 バイト＋コメント最大 65535 バイトの範囲にしか現れない。
        val lowerBound = (bytes.size - 22 - 0xFFFF).coerceAtLeast(0)
        for (index in bytes.size - 22 downTo lowerBound) {
            if (u32(bytes, index) == 0x06054b50L) return index
        }
        throw PixivApiException(0, "ugoira archive is not a valid ZIP")
    }

    private fun readEntry(
        bytes: ByteArray,
        entry: Entry,
    ): ByteArray {
        val header = entry.localHeaderOffset
        if (u32(bytes, header) != 0x04034b50L) throw PixivApiException(0, "ugoira archive has a corrupt local header")
        val nameLength = u16(bytes, header + 26)
        val extraLength = u16(bytes, header + 28)
        val dataStart = header + 30 + nameLength + extraLength
        val dataEnd = dataStart + entry.compressedSize.toInt()
        if (dataEnd > bytes.size) throw PixivApiException(0, "ugoira archive entry is truncated")
        return when (entry.compressionMethod) {
            0 -> bytes.copyOfRange(dataStart, dataEnd)
            8 -> inflateEntry(bytes, dataStart, entry.compressedSize.toInt(), entry.uncompressedSize.toInt())
            else -> throw PixivApiException(0, "ugoira archive uses an unsupported compression method")
        }
    }

    // --- zlib --------------------------------------------------------------

    private fun inflateEntry(
        source: ByteArray,
        offset: Int,
        compressedLength: Int,
        uncompressedLength: Int,
    ): ByteArray =
        memScoped {
            val output = ByteArray(uncompressedLength)
            val stream = alloc<z_stream>()
            val init = inflateInit2(stream.ptr, -MAX_WBITS)
            if (init != Z_OK) {
                throw PixivApiException(0, "ugoira inflate init failed")
            }
            val result =
                try {
                    output.usePinned { outPinned ->
                        source.usePinned { inPinned ->
                            stream.next_in = inPinned.addressOf(offset).reinterpret()
                            stream.avail_in = compressedLength.convert()
                            stream.next_out = outPinned.addressOf(0).reinterpret()
                            stream.avail_out = output.size.convert()
                            inflate(stream.ptr, Z_FINISH)
                        }
                    }
                } finally {
                    inflateEnd(stream.ptr)
                }
            if (result != Z_STREAM_END && result != Z_OK) {
                throw PixivApiException(0, "ugoira archive entry could not be decompressed")
            }
            output
        }

    // --- little endian helpers --------------------------------------------

    private fun u16(
        bytes: ByteArray,
        index: Int,
    ): Int = (bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8)

    private fun u32(
        bytes: ByteArray,
        index: Int,
    ): Long =
        (bytes[index].toLong() and 0xFF) or
            ((bytes[index + 1].toLong() and 0xFF) shl 8) or
            ((bytes[index + 2].toLong() and 0xFF) shl 16) or
            ((bytes[index + 3].toLong() and 0xFF) shl 24)

    private data class Entry(
        val name: String,
        val isDirectory: Boolean,
        val compressionMethod: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Int,
    )

    private companion object {
        const val MAX_WBITS = 15
    }
}
