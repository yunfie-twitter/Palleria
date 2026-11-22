package com.yunfie.illustia.data

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.AccountEditResult
import com.yunfie.illustia.models.pixiv.CurrentUserProfile
import com.yunfie.illustia.models.pixiv.UgoiraFrame
import com.yunfie.illustia.models.pixiv.UgoiraMetadata
import com.yunfie.illustia.models.pixiv.UgoiraMetadataResponse
import com.yunfie.illustia.models.pixiv.UgoiraZipUrls
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pixiv のワイヤ DTO をアプリのモデルへ変換する（作品・ユーザー・アカウント系）。
 * 旧 Rust 実装の `into_*` 変換をそのまま移植しており、URL フォールバックの優先順位まで
 * 同じ挙動を保つ。
 */
internal object PixivApiDecoder {
    fun illustPage(response: IllustPageResponse): PageResult<Illust> =
        PageResult(
            items = response.illusts.mapNotNull { PixivIllustMapper.toIllust(it) },
            nextUrl = response.nextUrl,
        )

    fun illustDetail(response: IllustDetailResponse): Illust =
        response.illust?.let { PixivIllustMapper.toIllust(it) }
            ?: throw PixivApiException(0, "invalid illust detail response: missing illust")

    fun userProfile(
        response: UserDetailResponse,
        fallbackUserId: Long,
    ): UserProfile {
        val profile = response.profile
        return UserProfile(
            id = if (response.user.id > 0) response.user.id else fallbackUserId,
            name = response.user.name,
            account = response.user.account,
            profileImageUrl = response.user.profileImageUrls.medium,
            backgroundImageUrl = profile?.backgroundImageUrl,
            comment = profile?.comment ?: "",
            isFollowed = response.user.isFollowed ?: false,
        )
    }

    fun autocomplete(response: AutocompleteResponse): List<String> = response.tags.mapNotNull { it.name }

    fun userPreviewPage(response: UserPreviewPageResponse): PageResult<UserPreview> =
        PageResult(
            items = response.userPreviews.mapNotNull { it.toPreview() },
            nextUrl = response.nextUrl,
        )

    fun ugoiraMetadata(response: UgoiraMetadataResponseDto): UgoiraMetadataResponse {
        val metadata = response.ugoiraMetadata
        return UgoiraMetadataResponse(
            UgoiraMetadata(
                zipUrls = UgoiraZipUrls(metadata?.zipUrls?.medium ?: ""),
                frames = metadata?.frames.orEmpty().map { UgoiraFrame(file = it.file, delay = it.delay) },
            ),
        )
    }

    fun optionalBoolean(response: SimpleBooleanResponse): Boolean? = response.showAi

    fun currentUserProfile(response: UserMeResponse): CurrentUserProfile {
        val profile =
            response.profile ?: throw PixivApiException(0, "invalid profile response: missing profile")
        val userId =
            profile.userId ?: throw PixivApiException(0, "invalid profile response: missing user id")
        return CurrentUserProfile(
            userId = userId,
            pixivId = profile.pixivId,
            name = profile.name,
            profileImageUrl = profile.profileImageUrls?.medium,
            isPremium = profile.isPremium,
            xRestrict = profile.xRestrict,
        )
    }

    fun accountEdit(response: AccountEditResponse): AccountEditResult =
        AccountEditResult(
            isSucceeded = response.body?.isSucceed ?: !response.error,
            message = response.message,
            validationErrors =
                response.body
                    ?.validationErrors
                    .orEmpty()
                    .mapValues { it.value.asMessage() },
        )

    private fun UserPreviewDto.toPreview(): UserPreview? {
        val user = user?.takeIf { it.id > 0 } ?: return null
        return UserPreview(
            id = user.id,
            name = user.name,
            account = user.account,
            profileImageUrl = user.profileImageUrls.medium,
            comment = user.comment ?: "",
            isFollowed = user.isFollowed ?: false,
            previewIllusts = illusts.mapNotNull { PixivIllustMapper.toIllust(it) },
        )
    }

    private fun JsonElement.asMessage(): String =
        when {
            this is JsonPrimitive && isString -> content
            this is JsonNull -> ""
            else -> toString()
        }
}
