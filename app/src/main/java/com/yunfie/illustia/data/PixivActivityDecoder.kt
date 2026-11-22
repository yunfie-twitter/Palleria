package com.yunfie.illustia.data

import com.yunfie.illustia.models.pixiv.BookmarkTag
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.CommentProfileImageUrls
import com.yunfie.illustia.models.pixiv.CommentResponse
import com.yunfie.illustia.models.pixiv.CommentStamp
import com.yunfie.illustia.models.pixiv.CommentUser
import com.yunfie.illustia.models.pixiv.NotificationContent
import com.yunfie.illustia.models.pixiv.NotificationListResult
import com.yunfie.illustia.models.pixiv.NotificationViewMore
import com.yunfie.illustia.models.pixiv.PixivNotification
import com.yunfie.illustia.models.pixiv.PixivStamp
import com.yunfie.illustia.models.pixiv.SpotlightArticle
import com.yunfie.illustia.models.pixiv.SpotlightResult
import com.yunfie.illustia.models.pixiv.TrendingTag
import com.yunfie.illustia.models.pixiv.UserFollowDetail

/** コメント・通知・スタンプ・トレンド・スポットライトなどアクティビティ系 DTO の変換。 */
internal object PixivActivityDecoder {
    fun commentPage(response: CommentPageResponse): CommentResponse =
        CommentResponse(
            totalComments = response.totalComments,
            comments = response.comments.map { it.toComment() },
            nextUrl = response.nextUrl,
        )

    fun notificationPage(response: NotificationPageResponse): NotificationListResult =
        NotificationListResult(
            notifications = response.notifications.mapNotNull { it.toNotification() },
            nextUrl = response.nextUrl,
        )

    fun userFollowDetail(response: UserFollowDetailResponse): UserFollowDetail =
        UserFollowDetail(
            isFollowed = response.isFollowed ?: response.isFollow ?: false,
            restrict = response.restrict,
        )

    fun stamps(response: StampResponse): List<PixivStamp> =
        response.stamps.mapNotNull { stamp ->
            val id = stamp.stampId ?: return@mapNotNull null
            val url = stamp.stampUrl ?: return@mapNotNull null
            PixivStamp(id, url)
        }

    fun trendingTags(response: TrendingTagResponse): List<TrendingTag> =
        response.trendTags.mapNotNull { item ->
            val tag = item.tag ?: return@mapNotNull null
            TrendingTag(
                tag = tag,
                translatedName = item.translatedName,
                illustId = item.illust?.id,
                thumbnailUrl = item.illust?.imageUrls?.medium,
            )
        }

    fun bookmarkTags(response: IllustBookmarkTagsResponse): List<BookmarkTag> =
        response.bookmarkTags
            .filter { it.name.trim().isNotEmpty() }
            .map { BookmarkTag(it.name, it.count) }

    fun spotlight(response: SpotlightResponse): SpotlightResult =
        SpotlightResult(
            articles =
                response.spotlightArticles.mapNotNull { article ->
                    val id = article.id ?: return@mapNotNull null
                    SpotlightArticle(
                        id = id,
                        title = article.title,
                        pureTitle = article.pureTitle,
                        thumbnail = article.thumbnail,
                        articleUrl = article.articleUrl,
                        publishDate = article.publishDate,
                    )
                },
            nextUrl = response.nextUrl,
        )

    private fun CommentDto.toComment(): Comment =
        Comment(
            id = id,
            comment = comment,
            date = date,
            user = user?.toCommentUser(),
            parentComment = parentComment?.toParentComment(),
            hasReplies = hasReplies,
            stamp = stamp?.let { CommentStamp(stampId = it.stampId, stampUrl = it.stampUrl) },
        )

    private fun CommentDto.toParentComment(): Comment =
        Comment(
            id = id,
            comment = comment,
            date = date,
            user = user?.toCommentUser(),
            parentComment = null,
            hasReplies = hasReplies,
            stamp = stamp?.let { CommentStamp(stampId = it.stampId, stampUrl = it.stampUrl) },
        )

    private fun CommentUserDto.toCommentUser(): CommentUser =
        CommentUser(
            id = id,
            name = name,
            account = account,
            profileImageUrls = CommentProfileImageUrls(profileImageUrls.medium ?: ""),
        )

    private fun NotificationDto.toNotification(): PixivNotification? {
        val id = id ?: return null
        return PixivNotification(
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
    }
}
