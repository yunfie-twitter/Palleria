package com.yunfie.illustia.data

import android.content.ContentResolver
import android.media.ExifInterface
import android.net.Uri
import com.yunfie.illustia.models.Illust
import java.io.File
import java.util.Locale

object ImageMetadataWriter {
    fun embedMetadata(
        file: File,
        illust: Illust?,
        extension: String = file.extension.lowercase(Locale.ROOT),
    ) {
        if (illust == null || !isSupported(extension)) return

        runCatching {
            val exif = ExifInterface(file.absolutePath)
            applyExifTags(exif, illust)
            exif.saveAttributes()
        }
    }

    /**
     * Rewrites the EXIF block of an already-written provider document (MediaStore / SAF) in place,
     * so saving only ever copies the image bytes once.
     */
    fun embedMetadata(
        contentResolver: ContentResolver,
        uri: Uri,
        extension: String,
        illust: Illust?,
    ) {
        if (illust == null || !isSupported(extension)) return

        runCatching {
            contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                val exif = ExifInterface(descriptor.fileDescriptor)
                applyExifTags(exif, illust)
                exif.saveAttributes()
            }
        }
    }

    private fun isSupported(extension: String): Boolean = extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS

    private val SUPPORTED_EXTENSIONS = listOf("jpg", "jpeg", "webp", "heic", "heif", "png")

    private fun applyExifTags(
        exif: ExifInterface,
        illust: Illust,
    ) {
        val pixivUrl = "https://www.pixiv.net/artworks/${illust.id}"
        val desc = "${illust.title} by ${illust.artistName} ($pixivUrl)"
        val tagsFormatted = illust.tags.joinToString(",")
        val userComment =
            "{\"id\":${illust.id},\"artist\":\"${illust.artistName}\",\"artistId\":${illust.artistId}," +
                "\"tags\":\"$tagsFormatted\",\"url\":\"$pixivUrl\"}"

        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, desc)
        exif.setAttribute(ExifInterface.TAG_ARTIST, illust.artistName)
        exif.setAttribute(ExifInterface.TAG_COPYRIGHT, "Pixiv / ${illust.artistName}")
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment)
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Palleria")
    }
}
