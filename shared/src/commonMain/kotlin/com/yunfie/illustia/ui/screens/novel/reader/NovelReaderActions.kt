package com.yunfie.illustia.ui.screens.novel.reader

import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelReadingStatus

/** 小説リーダーが ViewModel に要求する操作。 */
interface NovelReaderActions {
    fun openIllust(illustId: Long)

    fun openNovelById(
        novelId: Long,
        title: String = "",
    )

    fun toggleNovelBookmark(novel: NovelPreview)

    fun updateNovelProgress(
        novelId: Long,
        page: Int,
        totalPages: Int,
        status: NovelReadingStatus? = null,
    )

    suspend fun getIllustPreviewUrl(illustId: Long): String?

    fun updateNovelFontSize(value: Float)

    fun updateNovelLineSpacing(value: String)

    fun updateNovelTheme(value: String)

    fun updateNovelLayoutMode(value: String)

    fun updateNovelFontFamily(value: String)

    fun updateNovelTtsSpeechRate(value: Float)

    fun updateNovelTtsPitch(value: Float)

    fun updateNovelTtsVoiceName(value: String)

    fun updateNovelTtsSkipSymbols(value: Boolean)

    fun addNovelTtsDictionaryEntry(
        word: String,
        reading: String,
    )

    fun removeNovelTtsDictionaryEntry(word: String)
}
