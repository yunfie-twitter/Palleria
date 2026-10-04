package com.yunfie.illustia.data

import android.app.Application
import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.pixiv.IllustSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class HomeFeedSnapshotTest {
    @get:Rule val folder = TemporaryFolder()
    private val illust =
        Illust(
            42,
            "title",
            "manga",
            "caption",
            7,
            "artist",
            "avatar",
            "square",
            "medium",
            "large",
            "original",
            listOf("m1", "m2"),
            listOf("l1", "l2"),
            listOf("o1", "o2"),
            listOf("tag"),
            2,
            true,
            100,
            3,
            IllustSeries(9, "series"),
            1,
            2,
        )

    @Test fun restartPreservesCompleteArtworkWithoutCredentialsOrStalePagination() {
        HomeFeedSnapshot(folder.root).write(HomeFeedKind.Recommended, "secret-refresh", PageResult(listOf(illust), "next-page"), 1000)
        val restored = HomeFeedSnapshot(folder.root).read(HomeFeedKind.Recommended, "secret-refresh", 2000)
        assertEquals(listOf(illust), restored?.items)
        assertNull(restored?.nextUrl)
        assertFalse(File(folder.root, "Recommended.json").readText().contains("secret-refresh"))
    }

    @Test fun isolatesAccountAndFeedAndExpiresOrClearsSnapshot() {
        val cache = HomeFeedSnapshot(folder.root)
        cache.write(HomeFeedKind.Recommended, "one", PageResult(listOf(illust), null), 1000)
        assertNull(cache.read(HomeFeedKind.Recommended, "two", 2000))
        assertNull(cache.read(HomeFeedKind.New, "one", 2000))
        assertNull(cache.read(HomeFeedKind.Recommended, "one", 0))
        assertNull(cache.read(HomeFeedKind.Recommended, "one", 86_401_001))
        cache.clear()
        assertNull(cache.read(HomeFeedKind.Recommended, "one", 2000))
    }

    @Test fun corruptSnapshotIsIgnoredAndItemCountIsBounded() {
        val cache = HomeFeedSnapshot(folder.root)
        File(folder.root, "Recommended.json").writeText("broken")
        assertNull(cache.read(HomeFeedKind.Recommended, "one", 2000))
        cache.write(HomeFeedKind.Recommended, "one", PageResult(List(100) { illust.copy(id = it.toLong()) }, null), 1000)
        assertEquals(60, cache.read(HomeFeedKind.Recommended, "one", 2000)?.items?.size)
    }
}
