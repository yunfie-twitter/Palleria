package com.yunfie.illustia.models.pixiv

data class NotificationListResult(
    val notifications: List<PixivNotification>,
    val nextUrl: String?,
)

data class PixivNotification(
    val id: Long,
    val createdDatetime: String?,
    val type: Int,
    val content: NotificationContent?,
    val viewMore: NotificationViewMore?,
    val targetUrl: String?,
    val isRead: Boolean,
)

data class NotificationContent(
    val text: String?,
    val leftIcon: String?,
    val leftImage: String?,
    val rightIcon: String?,
    val rightImage: String?,
)

data class NotificationViewMore(
    val unreadExists: Boolean,
    val title: String?,
)

data class PixivStamp(
    val id: Long,
    val url: String,
)
