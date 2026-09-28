package com.yunfie.illustia.data

import android.media.ExifInterface
import com.yunfie.illustia.models.Illust
import java.io.File
import java.io.InputStream
import java.util.Locale

object ImageMetadataWriter {
    fun embedMetadata(
        file: File,
        illust: Illust?,
    ) {
        if (illust == null) return
        val ext = file.extension.lowercase(Locale.ROOT)
        // ExifInterface supports JPEG, WebP, etc.
        if (ext !in listOf("jpg", "jpeg", "webp", "heic", "heif", "png")) return

        runCatching {
            val exif = ExifInterface(file.absolutePath)
            applyExifTags(exif, illust)
            exif.saveAttributes()
        }
    }

    fun embedMetadataToStream(
        input: InputStream,
        cacheDir: File,
        extension: String,
        illust: Illust?,
    ): File {
        val tempFile = File.createTempFile("palleria_dl_", ".$extension", cacheDir)
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
        if (illust != null) {
            embedMetadata(tempFile, illust)
        }
        return tempFile
    }

    private fun applyExifTags(
        exif: ExifInterface,
        illust: Illust,
    ) {
        val pixivUrl = "https://www.pixiv.net/artworks/${illust.id}"
        val desc = "${illust.title} by ${illust.artistName} ($pixivUrl)"
        val tagsFormatted = illust.tags.joinToString(", ")
        val userComment = "{\"id\":${illust.id},\"artist\":\"${illust.artistName}\",\"tags\":\"$tagsFormatted\",\"url\":\"$pixivUrl\"}"

        exif.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, desc)
        exif.setAttribute(ExifInterface.TAG_ARTIST, illust.artistName)
        exif.setAttribute(ExifInterface.TAG_COPYRIGHT, "Pixiv / ${illust.artistName}")
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment)
        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "Palleria")
    }
}
