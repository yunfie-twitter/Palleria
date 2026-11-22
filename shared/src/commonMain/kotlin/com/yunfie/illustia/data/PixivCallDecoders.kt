package com.yunfie.illustia.data

import com.yunfie.illustia.models.pixiv.BookmarkTag
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.NotificationListResult
import com.yunfie.illustia.models.pixiv.PixivStamp
import com.yunfie.illustia.models.pixiv.SpotlightResult
import com.yunfie.illustia.models.pixiv.TrendingTag
import com.yunfie.illustia.models.pixiv.UserFollowDetail

/** アクティビティ系エンドポイント用のデコード（コメント・通知・スタンプ・トレンド・スポットライト）。 */
internal suspend fun PixivCall.awaitCommentPage(): CommentResponse =
    decode(CommentPageResponse.serializer()) { PixivActivityDecoder.commentPage(it) }

internal suspend fun PixivCall.awaitNotificationPage(): NotificationListResult =
    decode(NotificationPageResponse.serializer()) { PixivActivityDecoder.notificationPage(it) }

internal suspend fun PixivCall.awaitUserFollowDetail(): UserFollowDetail =
    decode(UserFollowDetailResponse.serializer()) { PixivActivityDecoder.userFollowDetail(it) }

internal suspend fun PixivCall.awaitStamps(): List<PixivStamp> = decode(StampResponse.serializer()) { PixivActivityDecoder.stamps(it) }

internal suspend fun PixivCall.awaitTrendingTags(): List<TrendingTag> =
    decode(TrendingTagResponse.serializer()) { PixivActivityDecoder.trendingTags(it) }

internal suspend fun PixivCall.awaitBookmarkTags(): List<BookmarkTag> =
    decode(IllustBookmarkTagsResponse.serializer()) { PixivActivityDecoder.bookmarkTags(it) }

internal suspend fun PixivCall.awaitSpotlight(): SpotlightResult =
    decode(SpotlightResponse.serializer()) { PixivActivityDecoder.spotlight(it) }
