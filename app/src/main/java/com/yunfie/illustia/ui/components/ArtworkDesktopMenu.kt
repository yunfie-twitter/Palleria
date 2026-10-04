package com.yunfie.illustia.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Browser
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.platform.ImageClipboardHelper
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

internal data class ArtworkDesktopActions(
    val bookmarkPrivately: (Illust) -> Unit,
    val saveAs: (Illust) -> Unit,
    val message: (String) -> Unit,
)

internal val LocalArtworkDesktopActions = staticCompositionLocalOf<ArtworkDesktopActions?> { null }

@Composable
internal fun rememberArtworkDesktopActions(viewModel: IllustiaViewModel): ArtworkDesktopActions {
    var pendingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { uri ->
        val url = pendingUrl
        pendingUrl = null
        if (uri != null && url != null) viewModel.saveImageToDocument(url, uri)
    }
    return remember(viewModel, launcher) {
        ArtworkDesktopActions(
            bookmarkPrivately = { if (!it.isBookmarked) viewModel.toggleBookmark(it, Restrict.Private) },
            saveAs = { illust ->
                val url = illust.originalImagePages.firstOrNull() ?: illust.originalImageUrl ?: illust.imageUrl
                pendingUrl = url
                val extension = Uri.parse(url).lastPathSegment?.substringAfterLast('.', "jpg") ?: "jpg"
                launcher.launch("${illust.id}_p0.$extension")
            },
            message = viewModel::showMessage,
        )
    }
}

@Composable
internal fun Modifier.artworkDesktopMenu(illust: Illust): Modifier {
    val actions = LocalArtworkDesktopActions.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuPosition by remember(illust.id) { mutableStateOf<Offset?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.015f else 1f, label = "artwork-hover")
    val copied = stringResource(R.string.copied_image_to_clipboard)
    val copyFailed = stringResource(R.string.copy_image_failed)
    val browserFailed = stringResource(R.string.error_browser_failed)
    val position = menuPosition
    if (position != null && actions != null) {
        Popup(
            popupPositionProvider = remember(position) { ArtworkMenuPosition(position) },
            onDismissRequest = { menuPosition = null },
            properties = PopupProperties(focusable = true),
        ) {
            Column(
                Modifier.background(MiuixTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                    .widthIn(min = 200.dp).padding(8.dp),
            ) {
                DesktopMenuItem(R.string.desktop_open_new_tab) {
                    menuPosition = null
                    val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
                        .resolveActivity(context.packageManager)?.packageName
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pixiv.net/artworks/${illust.id}"))
                            .putExtra(Browser.EXTRA_CREATE_NEW_TAB, true)
                        if (browser != null && browser != context.packageName) intent.setPackage(browser)
                        else intent.addCategory(Intent.CATEGORY_APP_BROWSER)
                        context.startActivity(intent)
                    }.onFailure { actions.message(browserFailed) }
                }
                DesktopMenuItem(R.string.action_copy_image) {
                    menuPosition = null
                    scope.launch {
                        val url = illust.originalImagePages.firstOrNull() ?: illust.originalImageUrl ?: illust.imageUrl
                        actions.message(if (ImageClipboardHelper.copyImageToClipboard(context, url)) copied else copyFailed)
                    }
                }
                if (!illust.isBookmarked) {
                    DesktopMenuItem(R.string.desktop_bookmark_private) {
                        menuPosition = null
                        actions.bookmarkPrivately(illust)
                    }
                }
                DesktopMenuItem(R.string.desktop_save_as) {
                    menuPosition = null
                    actions.saveAs(illust)
                }
            }
        }
    }
    return pointerHoverIcon(PointerIcon.Hand)
        .hoverable(interactions)
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .onGloballyPositioned { origin = it.positionInWindow() }
        .pointerInput(illust.id, actions != null) {
            if (actions == null) return@pointerInput
            awaitPointerEventScope {
                var secondaryClick = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) secondaryClick = true
                    if (secondaryClick) {
                        event.changes.forEach { it.consume() }
                        if (event.type == PointerEventType.Release) {
                            menuPosition = origin + event.changes.first().position
                            secondaryClick = false
                        }
                    }
                }
            }
        }
}

@Composable
private fun DesktopMenuItem(label: Int, onClick: () -> Unit) {
    Text(stringResource(label), modifier = Modifier.miuixClickable(onClick = onClick).padding(12.dp))
}

private class ArtworkMenuPosition(private val position: Offset) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(
            position.x.roundToInt().coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            position.y.roundToInt().coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
}
