package com.yunfie.illustia.nativebridge

import android.app.Application
import com.yunfie.illustia.models.Illust
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DynamicShortcutPublisherTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun `publishes the four destinations plus up to two recent artworks`() {
        val illusts = (1L..5L).map(::sampleIllust)

        val specs = DynamicShortcutPublisher.buildSpecs(context, illusts)

        specs shouldHaveSize 6
        specs.take(4).map { it.id } shouldBe listOf("dyn-search", "dyn-ranking", "dyn-bookmarks", "dyn-history")
        specs.drop(4).map { it.id } shouldBe listOf("art-1", "art-2")
    }

    @Test
    fun `artwork shortcut intents open the artwork deep link`() {
        val artworkSpec = DynamicShortcutPublisher.buildSpecs(context, listOf(sampleIllust(99L))).last()

        artworkSpec.id shouldBe "art-99"
        artworkSpec.intent?.dataString shouldBe "pixiv://illusts/99"
        artworkSpec.shortLabel.toString() shouldStartWith "Title 99"
    }

    @Test
    fun `works without any recent artworks`() {
        DynamicShortcutPublisher.buildSpecs(context, emptyList()) shouldHaveSize 4
    }
}

private fun sampleIllust(id: Long): Illust =
    Illust(
        id = id,
        title = "Title $id",
        type = "illust",
        caption = "",
        artistId = 1L,
        artistName = "Artist",
        artistAvatarUrl = null,
        squareImageUrl = "",
        imageUrl = "",
        originalImageUrl = null,
        tags = emptyList(),
        pageCount = 1,
        isBookmarked = false,
    )
