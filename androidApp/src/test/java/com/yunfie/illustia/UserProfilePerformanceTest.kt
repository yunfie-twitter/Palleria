package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.screens.profile.UserWorkSortOrder
import com.yunfie.illustia.ui.screens.profile.UserWorkTypeFilter
import com.yunfie.illustia.ui.screens.profile.processUserWorks
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfilePerformanceTest :
    FunSpec({
        test("revisiting restores accumulated pages and empty-but-loaded bookmarks without extending TTL") {
            var now = 0L
            val cache = UserProfileCache(ttlMillis = 100, clock = { now })
            val original = profileState(1, listOf(work(10), work(9)))
            cache.put(original)
            now = 99
            val restored = cache.get(1, original.settings)!!.restore(IllustiaUiState())
            restored.selectedUserIllusts shouldBe original.selectedUserIllusts
            restored.selectedUserNextUrl shouldBe "next-1"
            restored.selectedUserBookmarksLoaded shouldBe true
            restored.selectedUserBookmarks shouldBe emptyList()
            now = 100
            cache.get(1, original.settings) shouldBe null
        }

        test("cache never crosses accounts or content restrictions") {
            val cache = UserProfileCache()
            val original = profileState(1, listOf(work(10)))
            cache.put(original)
            cache.get(1, original.settings.copy(refreshToken = "another-account")) shouldBe null
            cache.put(original)
            cache.get(1, original.settings.copy(hideAiWorks = !original.settings.hideAiWorks)) shouldBe null
            cache.put(original)
            cache.get(1, original.settings.copy(mutedUsers = listOf(1))) shouldBe null
        }

        test("cache evicts whole profiles by LRU and total artwork budget") {
            val cache = UserProfileCache(maxProfiles = 2, maxItems = 3)
            cache.put(profileState(1, listOf(work(1))))
            cache.put(profileState(2, listOf(work(2))))
            cache.get(1, AppSettings())
            cache.put(profileState(3, listOf(work(3), work(4))))
            cache.get(2, AppSettings()) shouldBe null
            cache.get(1, AppSettings())!!.works.map { it.id } shouldBe listOf(1L)
            cache.put(profileState(4, (1L..4L).map { work(it) }))
            cache.get(4, AppSettings()) shouldBe null
        }

        test("cached bookmark and follow state stays current") {
            val cache = UserProfileCache()
            val original = profileState(1, listOf(work(1)))
            cache.put(original)
            cache.updateIllust(work(1).copy(isBookmarked = true))
            cache.updateUser(original.selectedUser!!.copy(isFollowed = true))
            cache
                .get(1, original.settings)!!
                .works
                .single()
                .isBookmarked shouldBe true
            cache.get(1, original.settings)!!.user.isFollowed shouldBe true
            cache.clear()
            cache.get(1, original.settings) shouldBe null
        }

        test("opening another profile cancels all requests including bookmark pagination") {
            runTest {
                val requests = UserProfileRequests(backgroundScope)
                val first = requests.open(1, "account")
                var cancelled = 0
                repeat(3) {
                    first.scope.launch {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled++
                        }
                    }
                }
                runCurrent()
                val second = requests.open(2, "account")
                runCurrent()
                cancelled shouldBe 3
                requests.isCurrent(first) shouldBe false
                requests.isCurrent(second) shouldBe true
                requests.close()
                requests.current() shouldBe null
            }
        }

        test("late non-cooperative response cannot affect a new visit to the same user") {
            runTest {
                val requests = UserProfileRequests(backgroundScope)
                val release = CompletableDeferred<Unit>()
                var applied = false
                val first = requests.open(1, "account")
                first.scope.launch {
                    withContext(NonCancellable) {
                        release.await()
                        if (requests.isCurrent(first)) applied = true
                    }
                }
                runCurrent()
                requests.open(1, "account")
                release.complete(Unit)
                runCurrent()
                applied shouldBe false
                requests.close()
            }
        }

        test("sorting preserves order semantics and does not mutate source pages") {
            val source = listOf(work(3, "manga", 5), work(1, bookmarks = 10), work(2, bookmarks = 20))
            processUserWorks(source, UserWorkSortOrder.Newest, UserWorkTypeFilter.All).map { it.id } shouldBe listOf(3L, 2L, 1L)
            processUserWorks(source, UserWorkSortOrder.Oldest, UserWorkTypeFilter.IllustOnly).map { it.id } shouldBe listOf(1L, 2L)
            processUserWorks(source, UserWorkSortOrder.MostBookmarks, UserWorkTypeFilter.All).map { it.id } shouldBe listOf(2L, 1L, 3L)
            processUserWorks(source, UserWorkSortOrder.Newest, UserWorkTypeFilter.MangaOnly).map { it.id } shouldBe listOf(3L)
            source.map { it.id } shouldBe listOf(3L, 1L, 2L)
        }

        test("closing profile resets request flags and completed page markers") {
            val closed =
                profileState(1, emptyList())
                    .copy(
                        isSelectedUserIllustsPaginating = true,
                        isSelectedUserBookmarksPaginating = true,
                    ).clearClosedUserPage()
            closed.isSelectedUserIllustsPaginating shouldBe false
            closed.isSelectedUserBookmarksPaginating shouldBe false
            closed.selectedUserIllustsLoaded shouldBe false
            closed.selectedUserBookmarksLoaded shouldBe false
        }
    })

private fun profileState(
    id: Long,
    works: List<Illust>,
): IllustiaUiState =
    IllustiaUiState(
        selectedUserId = id,
        selectedUser = UserProfile(id, "artist", "account", null, null, "", false),
        selectedUserIllusts = works,
        selectedUserNextUrl = "next-$id",
        selectedUserIllustsLoaded = true,
        selectedUserBookmarksLoaded = true,
    )

private fun work(
    id: Long,
    type: String = "illust",
    bookmarks: Int = 0,
): Illust =
    Illust(
        id = id,
        title = "work",
        type = type,
        caption = "",
        artistId = 1,
        artistName = "artist",
        artistAvatarUrl = null,
        squareImageUrl = "square",
        imageUrl = "image",
        originalImageUrl = null,
        tags = emptyList(),
        pageCount = 1,
        isBookmarked = false,
        totalBookmarks = bookmarks,
    )
