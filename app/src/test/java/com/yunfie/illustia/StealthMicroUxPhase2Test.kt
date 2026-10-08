package com.yunfie.illustia

import com.yunfie.illustia.data.ResumableDownloader
import com.yunfie.illustia.ui.components.MangaReadingDirection
import com.yunfie.illustia.ui.components.calculateMangaReadingDirection
import com.yunfie.illustia.ui.components.calculatePrefetchRange
import com.yunfie.illustia.ui.components.resolveAdaptivePreloadUrls
import io.kotest.matchers.shouldBe
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class StealthMicroUxPhase2Test {
    @get:Rule
    val tempFolder = TemporaryFolder()

    // ==========================================
    // 1. Velocity Landing Prefetch Tests
    // ==========================================

    @Test
    fun calculatePrefetchRange_emptyInputsReturnEmpty() {
        calculatePrefetchRange(-1, 20, 10, false) shouldBe IntRange.EMPTY
        calculatePrefetchRange(0, 10, 0, false) shouldBe IntRange.EMPTY
        calculatePrefetchRange(0, 0, 10, false) shouldBe IntRange.EMPTY
    }

    @Test
    fun calculatePrefetchRange_normalScroll() {
        // lastVisible = 5, total = 20, limit = 4 -> 6..9
        val range = calculatePrefetchRange(5, 20, 4, isFastScrollingDown = false)
        range shouldBe 6..9
    }

    @Test
    fun calculatePrefetchRange_normalScroll_clampedToEnd() {
        // lastVisible = 18, total = 20, limit = 4 -> 19..19
        val range = calculatePrefetchRange(18, 20, 4, isFastScrollingDown = false)
        range shouldBe 19..19
    }

    @Test
    fun calculatePrefetchRange_velocityLanding_triggeredOnFastFling() {
        // FAST_SCROLL_LANDING_OFFSET = 8
        // start = 5 + 8 = 13
        // end = min(13 + 4, 20) = 17 -> 13 until 17 -> 13..16
        val range =
            calculatePrefetchRange(
                lastVisibleIndex = 5,
                itemCount = 20,
                limit = 4,
                isFastScrollingDown = true,
            )
        range shouldBe 13..16
    }

    @Test
    fun calculatePrefetchRange_velocityLanding_clampedToEnd() {
        val range =
            calculatePrefetchRange(
                lastVisibleIndex = 16,
                itemCount = 20,
                limit = 4,
                isFastScrollingDown = true,
            )
        range shouldBe IntRange.EMPTY
    }

    // ==========================================
    // 2. Manga Adaptive Preloader Tests
    // ==========================================

    @Test
    fun calculateMangaReadingDirection_detectsForwardAndReverse() {
        // Initial state
        var direction = MangaReadingDirection.Forward

        // Forward reading (page 0 -> page 1)
        direction = calculateMangaReadingDirection(currentPage = 1, previousPage = 0, currentDirection = direction)
        direction shouldBe MangaReadingDirection.Forward

        // Forward reading (page 1 -> page 2)
        direction = calculateMangaReadingDirection(currentPage = 2, previousPage = 1, currentDirection = direction)
        direction shouldBe MangaReadingDirection.Forward

        // Reverse reading (page 2 -> page 1, e.g. manga right-to-left or checking previous)
        direction = calculateMangaReadingDirection(currentPage = 1, previousPage = 2, currentDirection = direction)
        direction shouldBe MangaReadingDirection.Reverse

        // Same page keeps current direction
        direction = calculateMangaReadingDirection(currentPage = 1, previousPage = 1, currentDirection = direction)
        direction shouldBe MangaReadingDirection.Reverse
    }

    @Test
    fun resolveAdaptivePreloadUrls_forwardPrioritizesUpcomingPages() {
        val pages = listOf("p0.jpg", "p1.jpg", "p2.jpg", "p3.jpg", "p4.jpg")
        val preload = resolveAdaptivePreloadUrls(currentPage = 1, imageUrls = pages, direction = MangaReadingDirection.Forward)
        // Forward: next pages (+1, +2), then previous (-1)
        preload shouldBe listOf("p2.jpg", "p3.jpg", "p0.jpg")
    }

    @Test
    fun resolveAdaptivePreloadUrls_reversePrioritizesPrecedingPages() {
        val pages = listOf("p0.jpg", "p1.jpg", "p2.jpg", "p3.jpg", "p4.jpg")
        val preload = resolveAdaptivePreloadUrls(currentPage = 3, imageUrls = pages, direction = MangaReadingDirection.Reverse)
        // Reverse: previous pages (-1, -2), then next (+1)
        preload shouldBe listOf("p2.jpg", "p1.jpg", "p4.jpg")
    }

    // ==========================================
    // 3. ResumableDownloader (Byte-Range Resume) Tests
    // ==========================================

    @Test
    fun resumableDownloader_freshDownload_writesFullContent() {
        val fullData = "CompleteArtworkPayload"
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    request.header("Range") shouldBe null
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(fullData.toResponseBody("image/jpeg".toMediaTypeOrNull()))
                        .build()
                }.build()

        val partFile = File(tempFolder.root, "test1.part")
        val result =
            ResumableDownloader.downloadToPartFile(
                client = client,
                url = "https://i.pximg.net/img-original/img/test1.jpg",
                partFile = partFile,
                resumeEnabled = true,
            )

        result.isResumed shouldBe false
        result.bytesDownloaded shouldBe fullData.toByteArray().size.toLong()
        partFile.readText() shouldBe fullData
    }

    @Test
    fun resumableDownloader_resumePartialDownload_sendsRangeAndAppends() {
        val initialPart = "FirstHalf_"
        val secondPart = "SecondHalf"
        val partFile = File(tempFolder.root, "test2.part")
        FileOutputStream(partFile).use { it.write(initialPart.toByteArray()) }

        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val range = request.header("Range")
                    range shouldBe "bytes=${initialPart.length}-"
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(206)
                        .message("Partial Content")
                        .body(secondPart.toResponseBody("image/jpeg".toMediaTypeOrNull()))
                        .build()
                }.build()

        val result =
            ResumableDownloader.downloadToPartFile(
                client = client,
                url = "https://i.pximg.net/img-original/img/test2.jpg",
                partFile = partFile,
                resumeEnabled = true,
            )

        result.isResumed shouldBe true
        result.bytesDownloaded shouldBe secondPart.toByteArray().size.toLong()
        partFile.readText() shouldBe (initialPart + secondPart)
    }

    @Test
    fun resumableDownloader_serverIgnoresRange_overwritesCleanly() {
        val staleData = "CorruptStaleData"
        val fullFreshData = "FreshNewPayload"
        val partFile = File(tempFolder.root, "test3.part")
        FileOutputStream(partFile).use { it.write(staleData.toByteArray()) }

        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    // Server returns 200 OK instead of 206
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(fullFreshData.toResponseBody("image/jpeg".toMediaTypeOrNull()))
                        .build()
                }.build()

        val result =
            ResumableDownloader.downloadToPartFile(
                client = client,
                url = "https://i.pximg.net/img-original/img/test3.jpg",
                partFile = partFile,
                resumeEnabled = true,
            )

        result.isResumed shouldBe false
        partFile.readText() shouldBe fullFreshData
    }

    @Test
    fun resumableDownloader_whenResumeDisabled_deletesPartAndDownloadsFresh() {
        val staleData = "OldPart"
        val freshData = "BrandNewData"
        val partFile = File(tempFolder.root, "test4.part")
        FileOutputStream(partFile).use { it.write(staleData.toByteArray()) }

        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    request.header("Range") shouldBe null
                    Response
                        .Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(freshData.toResponseBody("image/jpeg".toMediaTypeOrNull()))
                        .build()
                }.build()

        val result =
            ResumableDownloader.downloadToPartFile(
                client = client,
                url = "https://i.pximg.net/img-original/img/test4.jpg",
                partFile = partFile,
                resumeEnabled = false,
            )

        result.isResumed shouldBe false
        partFile.readText() shouldBe freshData
    }
}
