package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.screens.novel.reader.NovelReaderScreen as SharedNovelReaderScreen

@Composable
fun NovelReaderScreen(
    novel: NovelPreview?,
    text: NovelTextContent?,
    loadState: LoadState,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val isTtsEnabled = settings.isFeatureEnabled(FeatureFlag.NovelTtsAudiobook)
    val ttsPlayer = remember(context, isTtsEnabled) { if (isTtsEnabled) NovelTtsPlayer(context) else null }
    DisposableEffect(ttsPlayer) {
        onDispose {
            ttsPlayer?.shutdown()
        }
    }

    SharedNovelReaderScreen(
        novel = novel,
        text = text,
        loadState = loadState,
        settings = settings,
        viewModel = viewModel,
        platform = androidUserProfilePlatform(),
        tts = ttsPlayer,
        onBack = onBack,
        onRetry = onRetry,
    )
}
