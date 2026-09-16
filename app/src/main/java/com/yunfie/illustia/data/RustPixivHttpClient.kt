package com.yunfie.illustia.data

import android.os.Build
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NetworkMode
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.AccountEditResult
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.CommentProfileImageUrls
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.CommentStamp
import com.yunfie.illustia.models.pixiv.CommentUser
import com.yunfie.illustia.models.pixiv.CoverImageUrls
import com.yunfie.illustia.models.pixiv.CurrentUserProfile
import com.yunfie.illustia.models.pixiv.IllustSeries
import com.yunfie.illustia.models.pixiv.IllustSeriesDetail
import com.yunfie.illustia.models.pixiv.IllustSeriesProfileImageUrls
import com.yunfie.illustia.models.pixiv.IllustSeriesUser
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.Illusts
import com.yunfie.illustia.models.pixiv.ImageUrls
import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.models.pixiv.MangaSeriesProfileImageUrls
import com.yunfie.illustia.models.pixiv.MangaSeriesUser
import com.yunfie.illustia.models.pixiv.MetaPages
import com.yunfie.illustia.models.pixiv.MetaPagesImageUrls
import com.yunfie.illustia.models.pixiv.MetaSinglePage
import com.yunfie.illustia.models.pixiv.NotificationContent
import com.yunfie.illustia.models.pixiv.NotificationListResult
import com.yunfie.illustia.models.pixiv.NotificationViewMore
import com.yunfie.illustia.models.pixiv.PixivNotification
import com.yunfie.illustia.models.pixiv.PixivProfileImageUrls
import com.yunfie.illustia.models.pixiv.PixivStamp
import com.yunfie.illustia.models.pixiv.PixivTag
import com.yunfie.illustia.models.pixiv.PixivUser
import com.yunfie.illustia.models.pixiv.SpotlightArticle
import com.yunfie.illustia.models.pixiv.SpotlightResult
import com.yunfie.illustia.models.pixiv.TrendingTag
import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraMetadata
import com.yunfie.illustia.models.pixiv.UgoiraMetadataResponse
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.UgoiraPlaybackFrame
import com.yunfie.illustia.models.pixiv.UgoiraZipUrls
import com.yunfie.illustia.models.pixiv.UserFollowDetail
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel
import com.yunfie.illustia.rust.ApiException
import com.yunfie.illustia.rust.PixivHttpClient
import com.yunfie.illustia.rust.PixivRequest
import com.yunfie.illustia.settings.currentAcceptLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.ByteArrayOutputStream

/** OkHttp request builder compatibility layer backed by the UniFFI Rust client. */
internal class RustPixivHttpClient(
    mode: NetworkMode,
) {
    private val native =
        PixivHttpClient(
            networkMode = mode.code,
            userAgent = "PixivAndroidApp/${PixivApiConfig.APP_VERSION} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})",
            appOsVersion = "Android ${Build.VERSION.RELEASE}",
            acceptLanguage = currentAcceptLanguage(),
        )

    fun newCall(request: Request): RustPixivCall = RustPixivCall(native, request)

    suspend fun loginWithRefreshToken(refreshToken: String): PixivSession =
        withContext(Dispatchers.IO) {
            nativeCall {
                native.loginWithRefreshToken(refreshToken).let {
                    PixivSession(it.accessToken, it.refreshToken, it.userId?.toLong())
                }
            }
        }

    suspend fun loginWithAuthorizationCode(
        code: String,
        codeVerifier: String,
    ): PixivSession =
        withContext(Dispatchers.IO) {
            nativeCall {
                native.loginWithAuthorizationCode(code, codeVerifier).let {
                    PixivSession(it.accessToken, it.refreshToken, it.userId?.toLong())
                }
            }
        }

    fun createWebLoginUrl(
        createProvisionalAccount: Boolean,
        codeChallenge: String,
    ): String = native.createWebLoginUrl(createProvisionalAccount, codeChallenge)

    suspend fun prepareUgoira(
        url: String,
        cacheDir: String,
        frames: List<UgoiraFrame>,
    ): UgoiraPlayback =
        withContext(Dispatchers.IO) {
            nativeCall {
                native
                    .prepareUgoira(
                        url = url,
                        headers =
                            mapOf(
                                "Referer" to "https://www.pixiv.net/",
                                "User-Agent" to "PixivAndroidApp/6.184.0 (Android 14; Palleria)",
                            ),
                        cacheDir = cacheDir,
                        frames =
                            frames.map {
                                com.yunfie.illustia.rust.UgoiraFrame(
                                    file = it.file,
                                    delayMillis = it.delay,
                                )
                            },
                    ).let { playback ->
                        UgoiraPlayback(
                            frames =
                                playback.frames.map {
                                    UgoiraPlaybackFrame(
                                        filePath = it.file,
                                        delayMillis = it.delayMillis,
                                    )
                                },
                        )
                    }
            }
        }
}

internal class RustPixivCall(
    private val native: PixivHttpClient,
    private val request: Request,
) {
    suspend fun awaitIllustPage(): PageResult<Illust> =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeIllustPage(request = nativeRequest).let { page ->
                    PageResult(
                        items = page.items.map { it.toAppModel() },
                        nextUrl = page.nextUrl,
                    )
                }
            }
        }

    suspend fun awaitIllustDetail(): Illust =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeIllustDetail(request = nativeRequest).toAppModel()
            }
        }

    suspend fun awaitUserProfile(fallbackUserId: Long): UserProfile =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native
                    .executeUserProfile(
                        request = nativeRequest,
                        fallbackUserId = fallbackUserId,
                    ).toAppModel()
            }
        }

    suspend fun awaitAutocomplete(): List<String> =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeAutocomplete(request = nativeRequest).items
            }
        }

    suspend fun awaitUserPreviewPage(): PageResult<UserPreview> =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeUserPreviewPage(request = nativeRequest).let { page ->
                    PageResult(
                        items = page.items.map { it.toAppModel() },
                        nextUrl = page.nextUrl,
                    )
                }
            }
        }

    suspend fun awaitUgoiraMetadata(): UgoiraMetadataResponse =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeUgoiraMetadata(request = nativeRequest).let { metadata ->
                    UgoiraMetadataResponse(
                        UgoiraMetadata(
                            zipUrls = UgoiraZipUrls(metadata.zipUrl),
                            frames = metadata.frames.map { UgoiraFrame(file = it.file, delay = it.delayMillis) },
                        ),
                    )
                }
            }
        }

    suspend fun awaitCommentPage(): CommentResponse =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeCommentPage(request = nativeRequest).let { page ->
                    CommentResponse(
                        totalComments = page.totalComments,
                        comments = page.comments.map { it.toAppModel() },
                        nextUrl = page.nextUrl,
                    )
                }
            }
        }

    suspend fun awaitNotificationPage(): NotificationListResult =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeNotificationPage(request = nativeRequest).let { page ->
                    NotificationListResult(
                        notifications = page.notifications.map { it.toAppModel() },
                        nextUrl = page.nextUrl,
                    )
                }
            }
        }

    suspend fun awaitNovelPage(): PageResult<NovelPreview> =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeNovelPage(request = nativeRequest).let { page ->
                    PageResult(
                        items = page.items.map { it.toAppModel() },
                        nextUrl = page.nextUrl,
                    )
                }
            }
        }

    suspend fun awaitWatchlistManga(): WatchlistMangaModel =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeWatchlistManga(request = nativeRequest).let { page ->
                    WatchlistMangaModel(
                        series = page.series.map { it.toAppModel() },
                        nextUrl = page.nextUrl,
                    )
                }
            }
        }

    suspend fun awaitIllustSeries(): IllustSeriesWithIdModel =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeIllustSeriesPage(request = nativeRequest).let { page ->
                    IllustSeriesWithIdModel(
                        illustSeriesDetail =
                            page.detail?.let {
                                IllustSeriesDetail(
                                    height = it.height,
                                    seriesWorkCount = it.seriesWorkCount,
                                    id = it.id,
                                    createDate = it.createDate,
                                    title = it.title,
                                    width = it.width,
                                    coverImageUrls = CoverImageUrls(it.coverImageUrl),
                                    watchlistAdded = it.watchlistAdded,
                                    caption = it.caption,
                                    user =
                                        it.user?.let { user ->
                                            IllustSeriesUser(
                                                id = user.id,
                                                account = user.account,
                                                name = user.name,
                                                profileImageUrls = IllustSeriesProfileImageUrls(user.profileImageUrl),
                                                isFollowed = user.isFollowed,
                                            )
                                        },
                                )
                            },
                        illustSeriesFirstIllust = page.firstIllust?.toAppModel(),
                        illusts = page.illusts.map { it.toAppModel() },
                        nextUrl = page.nextUrl,
                    )
                }
            }
        }

    suspend fun awaitOptionalBoolean(): Boolean? =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeOptionalBoolean(request = nativeRequest).value
            }
        }

    suspend fun awaitUserFollowDetail(): UserFollowDetail =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native
                    .executeUserFollowDetail(request = nativeRequest)
                    .let { UserFollowDetail(isFollowed = it.isFollowed, restrict = it.restrict) }
            }
        }

    suspend fun awaitStamps(): List<PixivStamp> =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeStamps(request = nativeRequest).items.map { PixivStamp(it.id, it.url) }
            }
        }

    suspend fun awaitTrendingTags(): List<TrendingTag> =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeTrendingTags(request = nativeRequest).items.map {
                    TrendingTag(
                        tag = it.tag,
                        translatedName = it.translatedName,
                        illustId = it.illustId,
                        thumbnailUrl = it.thumbnailUrl,
                    )
                }
            }
        }

    suspend fun awaitSpotlight(): SpotlightResult =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeSpotlight(request = nativeRequest).let { page ->
                    SpotlightResult(
                        articles =
                            page.articles.map {
                                SpotlightArticle(
                                    id = it.id,
                                    title = it.title,
                                    pureTitle = it.pureTitle,
                                    thumbnail = it.thumbnail,
                                    articleUrl = it.articleUrl,
                                    publishDate = it.publishDate,
                                )
                            },
                        nextUrl = page.nextUrl,
                    )
                }
            }
        }

    suspend fun awaitCurrentUserProfile(): CurrentUserProfile =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeCurrentUserProfile(request = nativeRequest).let {
                    CurrentUserProfile(
                        userId = it.userId,
                        pixivId = it.pixivId,
                        name = it.name,
                        profileImageUrl = it.profileImageUrl,
                        isPremium = it.isPremium,
                        xRestrict = it.xRestrict,
                    )
                }
            }
        }

    suspend fun awaitAccountEdit(): AccountEditResult =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeAccountEdit(request = nativeRequest).let {
                    AccountEditResult(
                        isSucceeded = it.isSucceeded,
                        message = it.message,
                        validationErrors = it.validationErrors,
                    )
                }
            }
        }

    suspend fun awaitNovelText(novelId: Long): NovelTextContent =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native
                    .executeNovelText(
                        request = nativeRequest,
                        novelId = novelId,
                    ).let {
                        NovelTextContent(
                            novelId = it.novelId,
                            title = it.title,
                            text = it.text,
                            seriesPrevId = it.seriesPrevId,
                            seriesPrevTitle = it.seriesPrevTitle,
                            seriesNextId = it.seriesNextId,
                            seriesNextTitle = it.seriesNextTitle,
                        )
                    }
            }
        }

    suspend fun awaitComplete() =
        withContext(Dispatchers.IO) {
            val nativeRequest = lowerRequest()
            nativeCall {
                native.executeNoContent(request = nativeRequest)
            }
        }

    private fun lowerRequest(): PixivRequest {
        val sink = ByteArrayOutputStream()
        request.body?.let { body ->
            val bufferedSink = sink.sink().buffer()
            body.writeTo(bufferedSink)
            bufferedSink.flush()
        }
        val headers =
            buildMap {
                request.headers.names().forEach { name -> put(name, request.headers.values(name).joinToString(", ")) }
            }
        return PixivRequest(
            method = request.method,
            url = request.url.toString(),
            headers = headers,
            body = sink.toByteArray(),
            contentType = request.body?.contentType()?.toString(),
        )
    }
}

private fun com.yunfie.illustia.rust.Illust.toAppModel(): Illust =
    Illust(
        id = id,
        title = title,
        type = illustType,
        caption = caption,
        artistId = artistId,
        artistName = artistName,
        artistAvatarUrl = artistAvatarUrl,
        squareImageUrl = squareImageUrl,
        mediumImageUrl = mediumImageUrl,
        imageUrl = imageUrl,
        originalImageUrl = originalImageUrl,
        mediumImagePages = mediumImagePages,
        imagePages = imagePages,
        originalImagePages = originalImagePages,
        tags = tags,
        pageCount = pageCount,
        isBookmarked = isBookmarked,
        totalBookmarks = totalBookmarks,
        totalComments = totalComments,
        series = series?.let { IllustSeries(id = it.id, title = it.title) },
    )

private fun com.yunfie.illustia.rust.UserProfile.toAppModel(): UserProfile =
    UserProfile(
        id = id,
        name = name,
        account = account,
        profileImageUrl = profileImageUrl,
        backgroundImageUrl = backgroundImageUrl,
        comment = comment,
        isFollowed = isFollowed,
    )

private fun com.yunfie.illustia.rust.UserPreview.toAppModel(): UserPreview =
    UserPreview(
        id = id,
        name = name,
        account = account,
        profileImageUrl = profileImageUrl,
        comment = comment,
        isFollowed = isFollowed,
        previewIllusts = previewIllusts.map { it.toAppModel() },
    )

private fun com.yunfie.illustia.rust.Comment.toAppModel(): Comment =
    Comment(
        id = id,
        comment = comment,
        date = date,
        user = user?.toAppModel(),
        parentComment = parentComment?.toAppModel(),
        hasReplies = hasReplies,
        stamp = stamp?.toAppModel(),
    )

private fun com.yunfie.illustia.rust.ParentComment.toAppModel(): Comment =
    Comment(
        id = id,
        comment = comment,
        date = date,
        user = user?.toAppModel(),
        parentComment = null,
        hasReplies = hasReplies,
        stamp = stamp?.toAppModel(),
    )

private fun com.yunfie.illustia.rust.CommentUser.toAppModel(): CommentUser =
    CommentUser(
        id = id,
        name = name,
        account = account,
        profileImageUrls = CommentProfileImageUrls(profileImageUrl),
    )

private fun com.yunfie.illustia.rust.CommentStamp.toAppModel(): CommentStamp =
    CommentStamp(
        stampId = stampId,
        stampUrl = stampUrl,
    )

private fun com.yunfie.illustia.rust.Notification.toAppModel(): PixivNotification =
    PixivNotification(
        id = id,
        createdDatetime = createdDatetime,
        type = notificationType,
        content =
            content?.let {
                NotificationContent(
                    text = it.text,
                    leftIcon = it.leftIcon,
                    leftImage = it.leftImage,
                    rightIcon = it.rightIcon,
                    rightImage = it.rightImage,
                )
            },
        viewMore =
            viewMore?.let {
                NotificationViewMore(
                    unreadExists = it.unreadExists,
                    title = it.title,
                )
            },
        targetUrl = targetUrl,
        isRead = isRead,
    )

private fun com.yunfie.illustia.rust.NovelPreview.toAppModel(): NovelPreview =
    NovelPreview(
        id = id,
        title = title,
        caption = caption,
        userId = userId,
        userName = userName,
        userAccount = userAccount,
        coverUrl = coverUrl,
        pageCount = pageCount,
        textLength = textLength,
        isBookmarked = isBookmarked,
        totalBookmarks = totalBookmarks,
        totalView = totalView,
    )

private fun com.yunfie.illustia.rust.MangaSeries.toAppModel(): MangaSeriesModel =
    MangaSeriesModel(
        id = id,
        url = url,
        publishedContentCount = publishedContentCount,
        title = title,
        user =
            user?.let {
                MangaSeriesUser(
                    id = it.id,
                    name = it.name,
                    account = it.account,
                    profileImageUrls = it.profileImageUrl?.let(::MangaSeriesProfileImageUrls),
                )
            },
        lastPublishedContentDatetime = lastPublishedContentDatetime,
        latestContentId = latestContentId,
        thumbnailUrl = thumbnailUrl,
    )

private fun com.yunfie.illustia.rust.SeriesIllust.toAppModel(): Illusts =
    Illusts(
        id = id,
        title = title,
        type = illustType,
        imageUrls =
            ImageUrls(
                squareMedium = squareImageUrl,
                medium = mediumImageUrl,
                large = largeImageUrl,
            ),
        caption = caption,
        restrict = restrict,
        user =
            PixivUser(
                id = user.id,
                name = user.name,
                account = user.account,
                profileImageUrls = PixivProfileImageUrls(user.profileImageUrl),
                comment = user.comment,
                isFollowed = user.isFollowed,
            ),
        tags = tags.map { PixivTag(it) },
        tools = tools,
        createDate = createDate,
        pageCount = pageCount,
        width = width,
        height = height,
        sanityLevel = sanityLevel,
        xRestrict = xRestrict,
        metaSinglePage = if (hasMetaSinglePage) MetaSinglePage(originalImageUrl) else null,
        metaPages =
            metaPages.map {
                MetaPages(
                    MetaPagesImageUrls(
                        squareMedium = it.squareImageUrl,
                        medium = it.mediumImageUrl,
                        large = it.largeImageUrl,
                        original = it.originalImageUrl,
                    ),
                )
            },
        totalView = totalView,
        totalBookmarks = totalBookmarks,
        isBookmarked = isBookmarked,
        visible = visible,
        isMuted = isMuted,
        illustAIType = illustAiType,
        series = series?.let { IllustSeries(id = it.id, title = it.title) },
        illustBookStyle = illustBookStyle,
        totalComments = totalComments,
    )

private inline fun <T> nativeCall(block: () -> T): T =
    try {
        block()
    } catch (error: ApiException.Http) {
        throw PixivApiException(error.status.toInt(), error.detail, error)
    } catch (error: ApiException) {
        val detail =
            when (error) {
                is ApiException.InvalidRequest -> error.detail
                is ApiException.Network -> error.detail
                is ApiException.Http -> error.detail
                is ApiException.InvalidResponse -> error.detail
            }
        throw PixivApiException(0, detail, error)
    }
