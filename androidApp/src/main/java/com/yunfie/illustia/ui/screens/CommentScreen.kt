package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.data.pixiv.CommentArtworkType
import com.yunfie.illustia.ui.components.AVATAR_IMAGE_MAX_DECODE_DIMENSION_PX
import com.yunfie.illustia.ui.components.prefetchPixivThumbnail
import com.yunfie.illustia.uiRepository
import com.yunfie.illustia.ui.screens.CommentScreen as SharedCommentScreen

@Composable
fun CommentScreen(
    show: Boolean,
    id: Long,
    type: CommentArtworkType,
    viewModel: IllustiaViewModel,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onOpenUser: (Long) -> Unit,
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val repository = remember(context) { context.uiRepository() }
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    SharedCommentScreen(
        show = show,
        id = id,
        type = type,
        repository = repository,
        settings = settings,
        prefetchThumbnail = { url ->
            prefetchPixivThumbnail(
                applicationContext,
                url,
                decodeDimensionPx = AVATAR_IMAGE_MAX_DECODE_DIMENSION_PX,
            )
        },
        onDismiss = onDismiss,
        onBack = onBack,
        onOpenUser = onOpenUser,
    )
}
