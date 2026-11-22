package com.yunfie.illustia

import android.app.Application
import com.yunfie.illustia.data.FollowDeltaSyncManager
import com.yunfie.illustia.data.FollowFeedSnapshot
import com.yunfie.illustia.data.NetworkWarmer
import com.yunfie.illustia.models.Illust
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class NetworkSyncStealthTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun testIllust(id: Long) =
        Illust(
            id = id,
            title = "Title $id",
            type = "illust",
            caption = "",
            artistId = 100L,
            artistName = "Artist",
            artistAvatarUrl = null,
            squareImageUrl = "https://example.com/sq_$id.jpg",
            mediumImageUrl = "https://example.com/med_$id.jpg",
            imageUrl = "https://example.com/lg_$id.jpg",
            originalImageUrl = null,
            tags = listOf("tag1", "tag2"),
            pageCount = 1,
            isBookmarked = false,
        )

    // ========================================================
    // 1. FollowDeltaSyncManager Tests
    // ========================================================

    @Test
    fun calculateDelta_emptyCachedReturnsAllFreshItems() {
        val fresh = listOf(testIllust(1), testIllust(2), testIllust(3))
        val delta = FollowDeltaSyncManager.calculateDelta(emptyList(), fresh)
        delta shouldBe fresh
    }

    @Test
    fun calculateDelta_emptyFreshReturnsEmptyList() {
        val cached = listOf(testIllust(1), testIllust(2))
        val delta = FollowDeltaSyncManager.calculateDelta(cached, emptyList())
        delta shouldBe emptyList()
    }

    @Test
    fun calculateDelta_identicalListsReturnsEmptyList() {
        val items = listOf(testIllust(1), testIllust(2), testIllust(3))
        val delta = FollowDeltaSyncManager.calculateDelta(items, items)
        delta shouldBe emptyList()
    }

    @Test
    fun calculateDelta_filtersOutKnownIllustrationsPreservingFreshOrder() {
        val cached = listOf(testIllust(2), testIllust(3))
        val fresh = listOf(testIllust(5), testIllust(3), testIllust(4), testIllust(2), testIllust(1))
        val delta = FollowDeltaSyncManager.calculateDelta(cached, fresh)
        delta.map { it.id } shouldContainExactly listOf(5L, 4L, 1L)
    }

    // ========================================================
    // 2. FollowFeedSnapshot Tests
    // ========================================================

    @Test
    fun snapshot_saveAndReadRestoresItems() {
        val snapshot = FollowFeedSnapshot(tempFolder.root)
        val items = listOf(testIllust(10), testIllust(20))
        val token = "pixiv_refresh_token_test_123"

        snapshot.write(token, items, now = 1000L)
        val restored = snapshot.read(token, now = 2000L)

        restored shouldNotBe null
        restored!!.items shouldBe items
        restored.savedAt shouldBe 1000L
    }

    @Test
    fun snapshot_masksTokenAndIsolatesAccounts() {
        val snapshot = FollowFeedSnapshot(tempFolder.root)
        val items = listOf(testIllust(10))
        val token1 = "user_1_secret_token"
        val token2 = "user_2_secret_token"

        snapshot.write(token1, items, now = 1000L)

        // Reading with another token returns null
        snapshot.read(token2, now = 2000L) shouldBe null

        // Raw file never contains raw plaintext token
        val jsonFile = File(tempFolder.root, "following_feed.json")
        jsonFile.exists() shouldBe true
        jsonFile.readText().contains(token1) shouldBe false
        jsonFile.readText().contains(token2) shouldBe false
    }

    @Test
    fun snapshot_expiresAfterMaxAge() {
        val snapshot = FollowFeedSnapshot(tempFolder.root)
        val items = listOf(testIllust(10))
        val token = "refresh_token_expiry_test"
        val savedAt = 1000L
        val maxAgeMs = 48 * 60 * 60 * 1000L

        snapshot.write(token, items, now = savedAt)

        // Within validity window
        snapshot.read(token, now = savedAt + maxAgeMs - 1) shouldNotBe null

        // Beyond validity window
        snapshot.read(token, now = savedAt + maxAgeMs + 1) shouldBe null
    }

    @Test
    fun snapshot_clearDeletesFile() {
        val snapshot = FollowFeedSnapshot(tempFolder.root)
        snapshot.write("token", listOf(testIllust(1)))
        val jsonFile = File(tempFolder.root, "following_feed.json")
        jsonFile.exists() shouldBe true

        snapshot.clear()
        jsonFile.exists() shouldBe false
    }

    // ========================================================
    // 3. NetworkWarmer Tests
    // ========================================================

    @Test
    fun networkWarmer_warmUpTriggersDnsLookupAndSetsWarmedFlag() =
        runTest {
            NetworkWarmer.resetForTesting()
            NetworkWarmer.isWarmedForTesting() shouldBe false

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val testScope = TestScope(testDispatcher)

            val resolvedHosts = mutableListOf<String>()
            val mockDns =
                Dns { hostname ->
                    resolvedHosts.add(hostname)
                    listOf(InetAddress.getByName("127.0.0.1"))
                }
            val client = OkHttpClient.Builder().dns(mockDns).build()

            NetworkWarmer.warmUp(client, scope = testScope, dispatcher = testDispatcher)
            NetworkWarmer.isWarmedForTesting() shouldBe true

            // Calling again is idempotent and doesn't re-execute
            NetworkWarmer.warmUp(client, scope = testScope, dispatcher = testDispatcher)
            NetworkWarmer.isWarmedForTesting() shouldBe true

            testDispatcher.scheduler.advanceUntilIdle()
            // Host lookups should have executed
            resolvedHosts.isNotEmpty() shouldBe true
            resolvedHosts.contains("i.pximg.net") shouldBe true
            resolvedHosts.contains("app-api.pixiv.net") shouldBe true
        }
}
