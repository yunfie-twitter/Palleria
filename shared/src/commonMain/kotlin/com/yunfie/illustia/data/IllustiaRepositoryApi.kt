package com.yunfie.illustia.data

import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.AccountEditResult
import com.yunfie.illustia.models.pixiv.BookmarkTag
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.CurrentUserProfile
import com.yunfie.illustia.models.pixiv.NotificationListResult
import com.yunfie.illustia.models.pixiv.PixivStamp
import com.yunfie.illustia.models.pixiv.RelatedUsersResult
import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraMetadataResponse
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.UserProfileEdit
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import com.yunfie.illustia.settings.AppSettings

/**
 * ViewModel 層が Pixiv と設定に対して行う操作の境界。
 *
 * 実装（ネットワーク・キャッシュ・トークン更新）はプラットフォーム側に置いたまま、
 * ViewModel からはこのインターフェースだけを見せる。
 */
interface IllustiaRepositoryApi {
    /** アカウント切替の世代番号。値が変わったら古いレスポンスは破棄する。 */
    val accountGeneration: Long

    suspend fun readSettings(viewHistoryLimit: Int? = null): AppSettings

    suspend fun readFullViewHistory(): List<Illust>

    suspend fun readStartupSettings(): AppSettings

    /** フィード表示用の設定（可視フィルタに必要な項目のみ）。 */
    suspend fun readFeedSettings(): AppSettings

    suspend fun saveSettings(
        settings: AppSettings,
        baseSettings: AppSettings? = null,
    )

    suspend fun login(refreshToken: String): PixivSession

    suspend fun loginWithAuthorizationCode(
        code: String,
        codeVerifier: String,
    ): PixivSession

    suspend fun logout()

    suspend fun loadRanking(
        mode: String,
        forceRefresh: Boolean = false,
    ): PageResult<Illust>

    suspend fun followingIllusts(
        restrict: Restrict,
        forceRefresh: Boolean = false,
    ): PageResult<Illust>

    suspend fun loadHome(
        kind: HomeFeedKind,
        forceRefresh: Boolean = false,
    ): PageResult<Illust>

    /** 起動時に使うホームのキャッシュスナップショット。無ければ null。 */
    suspend fun readHomeSnapshot(kind: HomeFeedKind): PageResult<Illust>?

    suspend fun trendingTags(forceRefresh: Boolean = false): List<String>

    /** 検索語のオートコンプリート候補。 */
    suspend fun searchAutocomplete(
        word: String,
        forceRefresh: Boolean = false,
    ): List<String>

    /** 直近の [trendingTags] がオフラインの古いキャッシュで返されたか。 */
    fun trendingTagsServedStale(): Boolean

    suspend fun loadNovels(forceRefresh: Boolean = false): PageResult<NovelPreview>

    suspend fun nextNovelPage(nextUrl: String): PageResult<NovelPreview>

    suspend fun loadNovelText(
        novelId: Long,
        forceRefresh: Boolean = false,
    ): NovelTextContent

    suspend fun search(
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
        type: String? = null,
        forceRefresh: Boolean = false,
    ): PageResult<Illust>

    suspend fun searchNovels(
        word: String,
        sort: SearchSort,
        target: SearchTarget,
        duration: SearchDuration,
        bookmarkFilter: SearchBookmarkFilter,
        includeR18: Boolean,
        forceRefresh: Boolean = false,
    ): PageResult<NovelPreview>

    suspend fun searchUsers(
        word: String,
        forceRefresh: Boolean = false,
    ): PageResult<UserPreview>

    suspend fun popularPreview(
        word: String,
        forceRefresh: Boolean = false,
    ): PageResult<Illust>

    suspend fun illustComments(
        illustId: Long,
        offset: Int? = null,
        forceRefresh: Boolean = false,
    ): CommentResponse

    /** 期限内キャッシュ済みのコメント先頭ページ（未取得・期限切れなら null）。 */
    fun illustCommentsSnapshot(illustId: Long): CommentResponse?

    suspend fun illustCommentReplies(
        commentId: Long,
        offset: Int? = null,
        forceRefresh: Boolean = false,
    ): CommentResponse

    suspend fun novelCommentReplies(
        commentId: Long,
        forceRefresh: Boolean = false,
    ): CommentResponse

    suspend fun nextCommentPage(nextUrl: String): CommentResponse

    suspend fun addIllustComment(
        illustId: Long,
        comment: String,
        parentCommentId: Long? = null,
    )

    suspend fun addIllustStampComment(
        illustId: Long,
        stampId: Long,
        parentCommentId: Long? = null,
    )

    suspend fun addNovelComment(
        novelId: Long,
        comment: String,
        parentCommentId: Long? = null,
    )

    suspend fun novelComments(
        novelId: Long,
        forceRefresh: Boolean = false,
    ): CommentResponse

    suspend fun followingUsers(
        restrict: Restrict,
        forceRefresh: Boolean = false,
    ): PageResult<UserPreview>

    suspend fun userDetail(
        userId: Long,
        forceRefresh: Boolean = false,
    ): UserProfile

    suspend fun currentUserProfile(): CurrentUserProfile

    suspend fun relatedUsers(userId: Long): RelatedUsersResult

    suspend fun nextRelatedUsersPage(nextUrl: String): RelatedUsersResult

    suspend fun setUserProfile(profile: UserProfileEdit): AccountEditResult

    suspend fun reportIllust(
        illustId: Long,
        problemType: String? = null,
        message: String? = null,
    )

    suspend fun reportUser(
        userId: Long,
        problemType: String? = null,
        message: String? = null,
    )

    suspend fun addNovelBookmark(
        novelId: Long,
        restrict: Restrict,
    )

    suspend fun removeNovelBookmark(novelId: Long)

    suspend fun notifications(): NotificationListResult

    suspend fun notificationViewMore(notificationId: Long): NotificationListResult

    suspend fun nextNotificationPage(nextUrl: String): NotificationListResult

    suspend fun userIllusts(userId: Long): PageResult<Illust>

    suspend fun illustDetail(
        illustId: Long,
        forceRefresh: Boolean = false,
    ): Illust

    suspend fun stamps(): List<PixivStamp>

    suspend fun watchlistManga(forceRefresh: Boolean = false): WatchlistMangaModel

    suspend fun nextWatchlistMangaPage(nextUrl: String): WatchlistMangaModel

    suspend fun ugoiraMetadata(
        illustId: Long,
        forceRefresh: Boolean = false,
    ): UgoiraMetadataResponse

    suspend fun prepareUgoira(
        url: String,
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback

    suspend fun relatedIllusts(
        illustId: Long,
        forceRefresh: Boolean = false,
    ): PageResult<Illust>

    suspend fun followUser(
        userId: Long,
        restrict: Restrict,
    )

    suspend fun unfollowUser(userId: Long)

    suspend fun bookmarks(
        userId: Long,
        restrict: Restrict,
        forceRefresh: Boolean = false,
        tag: String? = null,
    ): PageResult<Illust>

    suspend fun illustBookmarkTags(
        userId: Long,
        restrict: Restrict,
    ): List<BookmarkTag>

    suspend fun nextPage(nextUrl: String): PageResult<Illust>

    suspend fun nextUserSearchPage(nextUrl: String): PageResult<UserPreview>

    suspend fun toggleBookmark(
        illust: Illust,
        restrict: Restrict,
    ): Illust
}
