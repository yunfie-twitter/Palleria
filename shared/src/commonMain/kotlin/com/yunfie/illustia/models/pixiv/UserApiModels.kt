package com.yunfie.illustia.models.pixiv

import com.yunfie.illustia.models.UserPreview

data class CurrentUserProfile(
    val userId: Long,
    val pixivId: String,
    val name: String,
    val profileImageUrl: String?,
    val isPremium: Boolean,
    val xRestrict: Int,
)

data class UserWorkspace(
    val pc: String = "",
    val monitor: String = "",
    val tool: String = "",
    val scanner: String = "",
    val tablet: String = "",
    val mouse: String = "",
    val printer: String = "",
    val desktop: String = "",
    val music: String = "",
    val desk: String = "",
    val chair: String = "",
    val comment: String = "",
    val workspaceImageUrl: String? = null,
)

data class UserProfileEdit(
    val gender: String,
    val address: Int,
    val country: String? = null,
    val job: Int,
    val userName: String,
    val birthday: String,
    val webpage: String = "",
    val twitter: String = "",
    val comment: String = "",
    val avatarJpeg: ByteArray? = null,
)

data class RelatedUsersResult(
    val users: List<UserPreview>,
    val nextUrl: String?,
)

data class AccountEditResult(
    val isSucceeded: Boolean,
    val message: String,
    val validationErrors: Map<String, String>,
)

data class SpotlightArticle(
    val id: Long,
    val title: String,
    val pureTitle: String,
    val thumbnail: String,
    val articleUrl: String,
    val publishDate: String,
)

data class SpotlightResult(
    val articles: List<SpotlightArticle>,
    val nextUrl: String?,
)

data class TrendingTag(
    val tag: String,
    val translatedName: String?,
    val illustId: Long?,
    val thumbnailUrl: String?,
)

data class UserFollowDetail(
    val isFollowed: Boolean,
    val restrict: String?,
) {
    val isPublicFollow: Boolean get() = isFollowed && restrict == "public"
    val isPrivateFollow: Boolean get() = isFollowed && restrict == "private"
}
