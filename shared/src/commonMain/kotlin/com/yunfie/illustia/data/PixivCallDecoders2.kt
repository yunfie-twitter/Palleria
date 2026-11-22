package com.yunfie.illustia.data

import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.pixiv.AccountEditResult
import com.yunfie.illustia.models.pixiv.CurrentUserProfile
import com.yunfie.illustia.models.pixiv.IllustSeriesWithIdModel
import com.yunfie.illustia.models.pixiv.WatchlistMangaModel

/** 小説・シリーズ・アカウント系エンドポイント用のデコード。 */
internal suspend fun PixivCall.awaitNovelPage(): PageResult<NovelPreview> =
    decode(NovelPageResponse.serializer()) { PixivNovelDecoder.novelPage(it) }

internal suspend fun PixivCall.awaitWatchlistManga(): WatchlistMangaModel =
    decode(WatchlistMangaResponse.serializer()) { PixivNovelDecoder.watchlistManga(it) }

internal suspend fun PixivCall.awaitIllustSeries(): IllustSeriesWithIdModel =
    decode(IllustSeriesPageResponse.serializer()) { PixivNovelDecoder.illustSeriesPage(it) }

internal suspend fun PixivCall.awaitOptionalBoolean(): Boolean? =
    decode(SimpleBooleanResponse.serializer()) { PixivApiDecoder.optionalBoolean(it) }

internal suspend fun PixivCall.awaitCurrentUserProfile(): CurrentUserProfile =
    decode(UserMeResponse.serializer()) { PixivApiDecoder.currentUserProfile(it) }

internal suspend fun PixivCall.awaitAccountEdit(): AccountEditResult =
    decode(AccountEditResponse.serializer()) { PixivApiDecoder.accountEdit(it) }
