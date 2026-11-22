package com.yunfie.illustia.platform

import android.media.ExifInterface
import java.io.File
import java.util.Locale

/**
 * Sanitizes privacy-sensitive Exif metadata (GPS location, device hardware info, serial numbers)
 * before sharing images externally, while safely preserving artwork copyright and creator tags.
 */
object PrivacyExifSanitizer {
    private val SENSITIVE_TAGS =
        listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DEVICE_SETTING_DESCRIPTION,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        )

    private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "webp", "heic", "heif", "png")

    fun sanitizeFile(file: File): Boolean {
        if (!file.exists() || !file.canWrite() || file.extension.lowercase(Locale.ROOT) !in SUPPORTED_EXTENSIONS) {
            return false
        }

        return runCatching {
            val exif = ExifInterface(file.absolutePath)
            var modified = false
            for (tag in SENSITIVE_TAGS) {
                if (exif.getAttribute(tag) != null) {
                    exif.setAttribute(tag, null)
                    modified = true
                }
            }
            if (modified) {
                exif.saveAttributes()
            }
            true
        }.getOrDefault(false)
    }
}
