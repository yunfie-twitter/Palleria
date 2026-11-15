package com.yunfie.illustia.ui.components

import com.yunfie.illustia.models.Illust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageQualityTiersTest {
    private fun illust(
        mediumPages: List<String> = emptyList(),
        imagePages: List<String> = emptyList(),
        originalPages: List<String> = emptyList(),
        medium: String = "",
        image: String = "image",
        original: String? = null,
        square: String = "square",
    ) = Illust(
        id = 1L,
        title = "t",
        type = "illust",
        caption = "",
        artistId = 1L,
        artistName = "a",
        artistAvatarUrl = null,
        squareImageUrl = square,
        mediumImageUrl = medium,
        imageUrl = image,
        originalImageUrl = original,
        mediumImagePages = mediumPages,
        imagePages = imagePages,
        originalImagePages = originalPages,
        tags = emptyList(),
        pageCount = 1,
        isBookmarked = false,
    )

    @Test
    fun `lower quality tier steps down once and stops at low`() {
        assertEquals(QUALITY_MEDIUM, lowerQualityTier(QUALITY_HIGH))
        assertEquals(QUALITY_LOW, lowerQualityTier(QUALITY_MEDIUM))
        assertEquals(null, lowerQualityTier(QUALITY_LOW))
        assertEquals(null, lowerQualityTier("unexpected"))
    }

    @Test
    fun `resolves each tier from its own page list`() {
        val target = illust(mediumPages = listOf("m"), imagePages = listOf("i"), originalPages = listOf("o"))
        assertEquals(listOf("m"), resolveQualityImageUrls(target, QUALITY_LOW))
        assertEquals(listOf("i"), resolveQualityImageUrls(target, QUALITY_MEDIUM))
        assertEquals(listOf("o"), resolveQualityImageUrls(target, QUALITY_HIGH))
    }

    @Test
    fun `empty page lists fall back to a lower tier`() {
        val target = illust(medium = "medium-single", image = "large-single")
        assertEquals(listOf("medium-single"), resolveQualityImageUrls(target, QUALITY_LOW))
        assertEquals(listOf("large-single"), resolveQualityImageUrls(target, QUALITY_MEDIUM))
        assertEquals(listOf("large-single"), resolveQualityImageUrls(target, QUALITY_HIGH))
    }

    @Test
    fun `placeholders stay empty when the feature is off or quality is low`() {
        val target = illust(imagePages = listOf("i"), mediumPages = listOf("m"))
        assertTrue(resolveProgressivePlaceholderUrls(target, QUALITY_MEDIUM, progressiveQuality = false).isEmpty())
        assertTrue(resolveProgressivePlaceholderUrls(target, QUALITY_LOW, progressiveQuality = true).isEmpty())
    }

    @Test
    fun `placeholders always use the thumbnail rather than the tier below`() {
        // Pixiv often serves the same URL for mediumImagePages and imagePages, so stepping down
        // one tier would hand PixivImage a placeholder equal to the real URL and drop the underlay.
        val target =
            illust(
                square = "square",
                medium = "medium",
                imagePages = listOf("medium"),
                mediumPages = listOf("medium"),
                originalPages = listOf("original"),
            )
        assertEquals(listOf("square"), resolveProgressivePlaceholderUrls(target, QUALITY_MEDIUM, progressiveQuality = true))
        assertEquals(listOf("square"), resolveProgressivePlaceholderUrls(target, QUALITY_HIGH, progressiveQuality = true))
    }

    @Test
    fun `placeholders stay empty without a distinct thumbnail`() {
        val target = illust(square = "square", medium = "square", imagePages = listOf("square"))
        assertTrue(resolveProgressivePlaceholderUrls(target, QUALITY_HIGH, progressiveQuality = true).isEmpty())
    }
}
