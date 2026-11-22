package com.yunfie.illustia.data

import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.pixiv.CoverImageUrls
import com.yunfie.illustia.models.pixiv.IllustSeriesDetail
import com.yunfie.illustia.models.pixiv.IllustSeriesProfileImageUrls
import com.yunfie.illustia.models.pixiv.IllustSeriesUser
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.MangaSeriesModel
import com.yunfie.illustia.models.pixiv.MangaSeriesProfileImageUrls
import com.yunfie.illustia.models.pixiv.MangaSeriesUser
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel

/** 小説・マンガ・シリーズ系 DTO の変換。 */
internal object PixivNovelDecoder {
    fun novelPage(response: NovelPageResponse): PageResult<NovelPreview> =
        PageResult(
            items = response.novels.mapNotNull { it.toNovelPreview() },
            nextUrl = response.nextUrl,
        )

    fun novelText(
        body: String,
        novelId: Long,
    ): NovelTextContent {
        val dto =
            extractNovelJson(body)?.let {
                runCatching {
                    PixivApiJson.json.decodeFromJsonElement(NovelTextDto.serializer(), it)
                }.getOrNull()
            } ?: throw PixivApiException(0, novelPreviewError(body))
        val navigation = dto.seriesNavigation
        return NovelTextContent(
            novelId = novelId,
            title = dto.title,
            text = if (dto.text.trim().isEmpty()) dto.novelText else dto.text,
            seriesPrevId = navigation?.prevNovel?.id,
            seriesPrevTitle = navigation?.prevNovel?.title,
            seriesNextId = navigation?.nextNovel?.id,
            seriesNextTitle = navigation?.nextNovel?.title,
        )
    }

    fun watchlistManga(response: WatchlistMangaResponse): WatchlistMangaModel =
        WatchlistMangaModel(
            series = response.series.mapNotNull { it.toSeries() },
            nextUrl = response.nextUrl,
        )

    fun illustSeriesPage(response: IllustSeriesPageResponse): IllustSeriesWithIdModel =
        IllustSeriesWithIdModel(
            illustSeriesDetail = response.illustSeriesDetail?.toDetail(),
            illustSeriesFirstIllust = response.illustSeriesFirstIllust?.let { PixivIllustMapper.toIllusts(it) },
            illusts = response.illusts.map { PixivIllustMapper.toIllusts(it) },
            nextUrl = response.nextUrl,
        )

    private fun NovelDto.toNovelPreview(): NovelPreview? {
        val id = id ?: return null
        return NovelPreview(
            id = id,
            title = title,
            caption = caption,
            userId = user.id,
            userName = user.name,
            userAccount = user.account,
            coverUrl = imageUrls.medium ?: "",
            pageCount = pageCount,
            textLength = textLength,
            isBookmarked = isBookmarked,
            totalBookmarks = totalBookmarks,
            totalView = totalView,
        )
    }

    private fun MangaSeriesDto.toSeries(): MangaSeriesModel? {
        val id = id ?: return null
        return MangaSeriesModel(
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
                        profileImageUrls = it.profileImageUrls?.medium?.let(::MangaSeriesProfileImageUrls),
                    )
                },
            lastPublishedContentDatetime = lastPublishedContentDatetime,
            latestContentId = latestContentId,
            thumbnailUrl = thumbnailUrl,
        )
    }

    private fun IllustSeriesDetailDto.toDetail(): IllustSeriesDetail? {
        val id = id ?: return null
        return IllustSeriesDetail(
            height = height,
            seriesWorkCount = seriesWorkCount,
            id = id,
            createDate = createDate,
            title = title,
            width = width,
            coverImageUrls = CoverImageUrls(coverImageUrls?.medium),
            watchlistAdded = watchlistAdded,
            caption = caption,
            user = user?.toSeriesUser(),
        )
    }

    private fun SeriesUserDto.toSeriesUser(): IllustSeriesUser? {
        val id = id ?: return null
        return IllustSeriesUser(
            id = id,
            account = account,
            name = name,
            profileImageUrls = IllustSeriesProfileImageUrls(profileImageUrls?.medium),
            isFollowed = isFollowed,
        )
    }

    private fun novelPreviewError(body: String): String {
        val preview =
            body
                .lineSequence()
                .take(8)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .take(320)
        return "novel response could not be parsed: $preview"
    }
}
