package com.yunfie.illustia.ui.components

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import com.yunfie.illustia.models.Illust
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ImageBlurPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `LocalImageBlurPreview defaults to true`() {
        var defaultValue: Boolean? = null
        compose.setContent {
            defaultValue = LocalImageBlurPreview.current
        }
        defaultValue shouldBe true
    }

    @Test
    fun `low-res placeholder is resolved when squareImageUrl differs from previewUrl`() {
        val illust =
            Illust(
                id = 12345L,
                title = "Test Artwork",
                type = "illust",
                caption = "",
                artistId = 1L,
                artistName = "Artist",
                artistAvatarUrl = null,
                squareImageUrl = "https://i.pximg.net/c/360x360_70/custom-thumb.jpg",
                mediumImageUrl = "https://i.pximg.net/c/600x1200_90/custom-medium.jpg",
                imageUrl = "https://i.pximg.net/img-master/custom-master.jpg",
                originalImageUrl = null,
                tags = emptyList(),
                pageCount = 1,
                isBookmarked = false,
            )

        val previewUrl = illust.previewUrl
        previewUrl shouldBe "https://i.pximg.net/c/600x1200_90/custom-medium.jpg"

        val placeholder = illust.squareImageUrl.takeIf { it.isNotBlank() && it != previewUrl }
        placeholder shouldBe "https://i.pximg.net/c/360x360_70/custom-thumb.jpg"
    }

    @Test
    fun `low-res placeholder is null when squareImageUrl matches previewUrl`() {
        val sameUrl = "https://i.pximg.net/c/360x360/fallback.jpg"
        val illust =
            Illust(
                id = 12346L,
                title = "Fallback Artwork",
                type = "illust",
                caption = "",
                artistId = 1L,
                artistName = "Artist",
                artistAvatarUrl = null,
                squareImageUrl = sameUrl,
                mediumImageUrl = "",
                imageUrl = sameUrl,
                originalImageUrl = null,
                tags = emptyList(),
                pageCount = 1,
                isBookmarked = false,
            )

        val previewUrl = illust.previewUrl
        val placeholder = illust.squareImageUrl.takeIf { it.isNotBlank() && it != previewUrl }
        placeholder.shouldBeNull()
    }

    @Test
    fun `low-res placeholder is null when squareImageUrl is blank`() {
        val illust =
            Illust(
                id = 12347L,
                title = "Blank Thumb Artwork",
                type = "illust",
                caption = "",
                artistId = 1L,
                artistName = "Artist",
                artistAvatarUrl = null,
                squareImageUrl = "",
                mediumImageUrl = "https://i.pximg.net/medium.jpg",
                imageUrl = "https://i.pximg.net/large.jpg",
                originalImageUrl = null,
                tags = emptyList(),
                pageCount = 1,
                isBookmarked = false,
            )

        val previewUrl = illust.previewUrl
        val placeholder = illust.squareImageUrl.takeIf { it.isNotBlank() && it != previewUrl }
        placeholder.shouldBeNull()
    }
}
