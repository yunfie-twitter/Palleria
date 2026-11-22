package com.yunfie.illustia.data

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.IllustSeries
import com.yunfie.illustia.models.pixiv.Illusts
import com.yunfie.illustia.models.pixiv.ImageUrls
import com.yunfie.illustia.models.pixiv.MetaPages
import com.yunfie.illustia.models.pixiv.MetaPagesImageUrls
import com.yunfie.illustia.models.pixiv.MetaSinglePage
import com.yunfie.illustia.models.pixiv.PixivProfileImageUrls
import com.yunfie.illustia.models.pixiv.PixivTag
import com.yunfie.illustia.models.pixiv.PixivUser

/**
 * IllustDto からアプリの Illust / Illusts へのマッピング。URL フォールバックの優先順位は
 * 旧 Rust 実装と同じ。
 */
internal object PixivIllustMapper {
    fun toIllust(dto: IllustDto): Illust? {
        val id = dto.id ?: return null
        val pages = collectPages(dto.metaPages)
        val original = dto.metaSinglePage?.effectiveOriginal ?: pages.original.firstOrNull()
        return Illust(
            id = id,
            title = dto.title,
            type = dto.illustType,
            caption = dto.caption,
            artistId = dto.user.id,
            artistName = dto.user.name,
            artistAvatarUrl = dto.user.profileImageUrls.medium,
            squareImageUrl = dto.imageUrls.squareMedium ?: "",
            mediumImageUrl = resolveMedium(dto.imageUrls, original),
            imageUrl = resolveLarge(dto.imageUrls, original),
            originalImageUrl = original,
            mediumImagePages = fallbackPages(pages.medium, pages.image, pages.original),
            imagePages = fallbackPages(pages.image, pages.original, emptyList()),
            originalImagePages = pages.original,
            tags = dto.tags.mapNotNull { it.name },
            pageCount = dto.pageCount,
            isBookmarked = dto.isBookmarked,
            totalBookmarks = dto.totalBookmarks,
            totalComments = dto.totalComments,
            series = dto.series?.let { IllustSeries(id = it.id, title = it.title) },
            xRestrict = dto.xRestrict,
            illustAiType = dto.illustAiType,
            width = dto.width,
            height = dto.height,
        )
    }

    fun toIllusts(dto: IllustDto): Illusts =
        Illusts(
            id = dto.id ?: 0L,
            title = dto.title,
            type = dto.illustType,
            imageUrls = contentImageUrls(dto.imageUrls),
            caption = dto.caption,
            restrict = 0,
            user = contentUser(dto.user),
            tags = dto.tags.mapNotNull { it.name?.let(::PixivTag) },
            tools = emptyList(),
            createDate = "",
            pageCount = dto.pageCount,
            width = dto.width,
            height = dto.height,
            sanityLevel = dto.sanityLevel,
            xRestrict = dto.xRestrict,
            metaSinglePage = dto.metaSinglePage?.effectiveOriginal?.let(::MetaSinglePage),
            metaPages = dto.metaPages.map { metaPage(it) },
            totalView = 0,
            totalBookmarks = dto.totalBookmarks,
            isBookmarked = dto.isBookmarked,
            visible = true,
            isMuted = false,
            illustAIType = dto.illustAiType,
            series = dto.series?.let { IllustSeries(id = it.id, title = it.title) },
            illustBookStyle = null,
            totalComments = dto.totalComments,
        )

    private class PageUrls(
        val medium: MutableList<String>,
        val image: MutableList<String>,
        val original: MutableList<String>,
    )

    private fun collectPages(metaPages: List<MetaPageDto>): PageUrls {
        val pages = PageUrls(ArrayList(metaPages.size), ArrayList(metaPages.size), ArrayList(metaPages.size))
        for (page in metaPages) {
            val urls = page.imageUrls ?: continue
            (urls.medium ?: urls.large)?.let { pages.medium.add(it) }
            (urls.large ?: urls.medium)?.let { pages.image.add(it) }
            (urls.effectiveOriginal ?: urls.large)?.let { pages.original.add(it) }
        }
        return pages
    }

    private fun resolveMedium(
        urls: ImageUrlsDto,
        original: String?,
    ): String =
        urls.medium
            ?: urls.squareMedium
            ?: urls.large
            ?: original
            ?: ""

    private fun resolveLarge(
        urls: ImageUrlsDto,
        original: String?,
    ): String =
        urls.large
            ?: urls.medium
            ?: original
            ?: ""

    private fun contentImageUrls(urls: ImageUrlsDto): ImageUrls =
        ImageUrls(
            squareMedium = urls.squareMedium ?: "",
            medium = urls.medium ?: "",
            large = urls.large ?: "",
        )

    private fun contentUser(user: UserDto): PixivUser =
        PixivUser(
            id = user.id,
            name = user.name,
            account = "",
            profileImageUrls = PixivProfileImageUrls(user.profileImageUrls.medium.orEmpty()),
            comment = null,
            isFollowed = null,
        )

    private fun metaPage(page: MetaPageDto): MetaPages {
        val urls = page.imageUrls
        return MetaPages(
            MetaPagesImageUrls(
                squareMedium = "",
                medium = urls?.medium ?: "",
                large = urls?.large ?: "",
                original = urls?.effectiveOriginal ?: "",
            ),
        )
    }

    private fun fallbackPages(
        primary: List<String>,
        secondary: List<String>,
        tertiary: List<String>,
    ): List<String> =
        when {
            primary.isNotEmpty() -> primary
            secondary.isNotEmpty() -> secondary
            else -> tertiary
        }
}
