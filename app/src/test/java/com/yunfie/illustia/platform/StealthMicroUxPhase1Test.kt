package com.yunfie.illustia.platform

import android.content.Context
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.buildSmartDownloadFilename
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StealthMicroUxPhase1Test {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `cleanTrackingParameters removes query params like s utm and ref while preserving normal queries`() {
        val dirtyTwitterUrl = "https://www.pixiv.net/artworks/12345678?s=09&utm_source=twitter&utm_medium=social"
        NativeIntentRouter.cleanTrackingParameters(dirtyTwitterUrl) shouldBe "https://www.pixiv.net/artworks/12345678"

        val mixedUrl = "https://www.pixiv.net/artworks/12345678?page=2&s=20&utm_campaign=share"
        NativeIntentRouter.cleanTrackingParameters(mixedUrl) shouldBe "https://www.pixiv.net/artworks/12345678?page=2"

        val cleanUrl = "https://www.pixiv.net/artworks/12345678"
        NativeIntentRouter.cleanTrackingParameters(cleanUrl) shouldBe cleanUrl
    }

    @Test
    fun `sanitizeSearchInput extracts pure artwork or user id from pasted dirty URLs`() {
        val pastedArtworkUrl = "https://www.pixiv.net/artworks/98765432?s=09&utm_source=share"
        NativeIntentRouter.sanitizeSearchInput(pastedArtworkUrl, enabled = true) shouldBe "98765432"

        val pastedUserUrl = "https://www.pixiv.net/users/5551234?ref=app"
        NativeIntentRouter.sanitizeSearchInput(pastedUserUrl, enabled = true) shouldBe "5551234"

        val plainQuery = "初音ミク"
        NativeIntentRouter.sanitizeSearchInput(plainQuery, enabled = true) shouldBe "初音ミク"

        // When disabled, keep as-is
        NativeIntentRouter.sanitizeSearchInput(pastedArtworkUrl, enabled = false) shouldBe pastedArtworkUrl
    }

    @Test
    fun `parseText resolves artwork even with dirty tracking queries`() {
        val dirtyUrl = "Check this out https://www.pixiv.net/artworks/11223344?s=09&utm_medium=android!"
        val event = NativeIntentRouter.parseText(dirtyUrl)
        event shouldBe NativeIntentEvent.Artwork(11223344L)
    }

    @Test
    fun `buildSmartDownloadFilename formats filename as Artist Title with page suffix`() {
        val illust =
            Illust(
                id = 123456L,
                title = "星空の少女",
                type = "illust",
                caption = "",
                artistId = 789L,
                artistName = "山田太郎",
                artistAvatarUrl = null,
                squareImageUrl = "square",
                imageUrl = "image",
                originalImageUrl = "original",
                tags = listOf("オリジナル", "風景"),
                pageCount = 1,
                isBookmarked = false,
            )

        buildSmartDownloadFilename(illust, page = 0, pageCount = 1) shouldBe "[山田太郎] 星空の少女"
        buildSmartDownloadFilename(illust, page = 0, pageCount = 5) shouldBe "[山田太郎] 星空の少女_p0"
        buildSmartDownloadFilename(illust, page = 3, pageCount = 5) shouldBe "[山田太郎] 星空の少女_p3"
    }

    @Test
    fun `PrivacyExifSanitizer removes GPS and device tags while preserving safe file`() {
        val testFile = File(context.cacheDir, "test_privacy.jpg")
        testFile.outputStream().use { out ->
            // Minimal valid JPEG header with SOI, APP1 (Exif), EOI
            val minimalJpeg =
                byteArrayOf(
                    0xFF.toByte(),
                    0xD8.toByte(), // SOI
                    0xFF.toByte(),
                    0xE0.toByte(),
                    0x00.toByte(),
                    0x10.toByte(), // APP0
                    0x4A.toByte(),
                    0x46.toByte(),
                    0x49.toByte(),
                    0x46.toByte(),
                    0x00.toByte(), // JFIF
                    0x01.toByte(),
                    0x01.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0x01.toByte(),
                    0x00.toByte(),
                    0x01.toByte(),
                    0x00.toByte(),
                    0x00.toByte(),
                    0xFF.toByte(),
                    0xD9.toByte(), // EOI
                )
            out.write(minimalJpeg)
        }

        val exif = ExifInterface(testFile.absolutePath)
        exif.setAttribute(ExifInterface.TAG_MODEL, "Pixel 8 Pro")
        exif.setAttribute(ExifInterface.TAG_ARTIST, "Palleria Artist")
        exif.saveAttributes()

        PrivacyExifSanitizer.sanitizeFile(testFile) shouldBe true

        val sanitizedExif = ExifInterface(testFile.absolutePath)
        sanitizedExif.getAttribute(ExifInterface.TAG_MODEL) shouldBe null
        sanitizedExif.getAttribute(ExifInterface.TAG_ARTIST) shouldBe "Palleria Artist"

        testFile.delete()
    }
}
