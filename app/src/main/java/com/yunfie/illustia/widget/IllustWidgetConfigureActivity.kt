package com.yunfie.illustia.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaApplication
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.Illust
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.screens.OnboardingScreen
import com.yunfie.illustia.ui.screens.RefreshTokenLoginBottomSheet
import com.yunfie.illustia.ui.screens.SearchScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

class IllustWidgetConfigureActivity : FragmentActivity() {
    private val widgetImageMaxDimension = 960

    private val viewModel by viewModels<IllustiaViewModel> {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    private val widgetId: Int by lazy {
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val widgetQuality = state.settings.fullscreenQuality
            var showTokenLogin by remember { mutableStateOf(false) }
            var pendingIllustId by rememberSaveable { mutableStateOf<Long?>(null) }
            var selectedPageIndex by rememberSaveable { mutableIntStateOf(0) }
            val scope = rememberCoroutineScope()

            MiuixTheme {
                BackHandler {
                    finish()
                }

                if (state.settings.refreshToken.isBlank()) {
                    OnboardingScreen(
                        state = state,
                        viewModel = viewModel,
                        onRefreshTokenLogin = { showTokenLogin = true },
                        showTokenLogin = showTokenLogin,
                        onTokenLoginDismiss = { showTokenLogin = false },
                    )
                } else {
                    Scaffold(
                        containerColor = MiuixTheme.colorScheme.surface,
                        topBar = {
                            SmallTopAppBar(
                                title = stringResource(R.string.widget_illust_title),
                                color = MiuixTheme.colorScheme.surface,
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
                                    }
                                },
                            )
                        },
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                        ) {
                            SearchScreen(
                                state = state,
                                viewModel = viewModel,
                                widgetSelectionMode = true,
                                onIllustSelected = { illust ->
                                    pendingIllustId = illust.id
                                    selectedPageIndex = 0
                                    viewModel.openIllust(illust.id)
                                },
                            )

                            if (showTokenLogin) {
                                RefreshTokenLoginBottomSheet(
                                    state = state,
                                    viewModel = viewModel,
                                    onDismiss = { showTokenLogin = false },
                                )
                            }

                            val pendingIllust = state.selectedIllust?.takeIf { it.id == pendingIllustId }
                            if (pendingIllust != null) {
                                WidgetPagePickerSheet(
                                    illust = pendingIllust,
                                    quality = widgetQuality,
                                    initialPageIndex = selectedPageIndex,
                                    onPageSelected = { selectedPageIndex = it },
                                    onCancel = {
                                        pendingIllustId = null
                                        viewModel.closeIllust()
                                    },
                                    onApply = { pageIndex ->
                                        scope.launch {
                                            runCatching {
                                                saveSelection(widgetId, pendingIllust, pageIndex)
                                            }.onSuccess {
                                                IllustWidgetProvider.updateWidget(
                                                    this@IllustWidgetConfigureActivity,
                                                    AppWidgetManager.getInstance(this@IllustWidgetConfigureActivity),
                                                    widgetId,
                                                )
                                                setResult(
                                                    Activity.RESULT_OK,
                                                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
                                                )
                                                finish()
                                            }
                                        }
                                    },
                                )
                            } else if (pendingIllustId != null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LoadingIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun saveSelection(widgetId: Int, illust: Illust, pageIndex: Int) {
        val urls = widgetPageUrls(illust, viewModel.uiState.value.settings.fullscreenQuality)
        val selectedUrl = urls.getOrNull(pageIndex) ?: urls.firstOrNull() ?: throw IOException("No page urls")
        val imageFile = downloadWidgetImage(selectedUrl, widgetId, pageIndex)
        val selection = IllustWidgetSelection(
            illustId = illust.id,
            pageIndex = pageIndex.coerceIn(0, urls.lastIndex.coerceAtLeast(0)),
            pageCount = urls.size.coerceAtLeast(1),
            title = illust.title,
            artistName = illust.artistName,
            imageUrl = selectedUrl,
            imagePath = imageFile.absolutePath,
        )
        IllustWidgetStore(this).save(widgetId, selection)
    }

    private suspend fun downloadWidgetImage(imageUrl: String, widgetId: Int, pageIndex: Int): File {
        return withContext(Dispatchers.IO) {
            val app = application as IllustiaApplication
            val request = Request.Builder()
                .url(imageUrl)
                .header("Referer", "https://www.pixiv.net/")
                .header("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Illustia)")
                .build()
            val response = app.sharedHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) throw IOException("Widget image download failed: ${resp.code}")
                val imageDir = File(filesDir, "widget_images").apply { mkdirs() }
                val rawFile = File(imageDir, "illust_${widgetId}_p$pageIndex.raw")
                val outputFile = File(imageDir, "illust_${widgetId}_p$pageIndex.jpg")
                resp.body.use { body ->
                    FileOutputStream(rawFile).use { output -> body.byteStream().use { input -> input.copyTo(output) } }
                }
                val bitmap = decodeWidgetBitmap(rawFile, widgetImageMaxDimension)
                    ?: throw IOException("Widget image decode failed")
                FileOutputStream(outputFile).use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                        throw IOException("Widget image encode failed")
                    }
                }
                if (!rawFile.delete()) {
                    rawFile.deleteOnExit()
                }
                outputFile
            }
        }
    }

    private fun decodeWidgetBitmap(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension, maxDimension)
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            },
        ) ?: return null

        if (decoded.width <= maxDimension && decoded.height <= maxDimension) {
            return decoded
        }

        val scale = minOf(
            maxDimension.toFloat() / decoded.width.toFloat(),
            maxDimension.toFloat() / decoded.height.toFloat(),
        )
        val targetWidth = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        if (scaled != decoded) {
            decoded.recycle()
        }
        return scaled
    }

    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        var halfHeight = srcHeight / 2
        var halfWidth = srcWidth / 2
        while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun widgetPageUrls(illust: Illust, quality: String): List<String> {
        return when (quality) {
            "low" -> illust.mediumImagePages.ifEmpty {
                listOf(
                    illust.mediumImageUrl.ifBlank {
                        illust.squareImageUrl.ifBlank { illust.imageUrl }
                    },
                )
            }
            "medium" -> illust.imagePages.ifEmpty { listOf(illust.imageUrl) }
            else -> illust.originalImagePages.ifEmpty {
                illust.imagePages.ifEmpty { listOfNotNull(illust.originalImageUrl ?: illust.imageUrl) }
            }
        }.ifEmpty { listOf(illust.imageUrl) }
    }
}

@Composable
private fun WidgetPagePickerSheet(
    illust: Illust,
    quality: String,
    initialPageIndex: Int,
    onPageSelected: (Int) -> Unit,
    onCancel: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val urls = remember(illust, quality) {
        when (quality) {
            "low" -> illust.mediumImagePages.ifEmpty {
                listOf(
                    illust.mediumImageUrl.ifBlank {
                        illust.squareImageUrl.ifBlank { illust.imageUrl }
                    },
                )
            }
            "medium" -> illust.imagePages.ifEmpty { listOf(illust.imageUrl) }
            else -> illust.originalImagePages.ifEmpty {
                illust.imagePages.ifEmpty { listOfNotNull(illust.originalImageUrl ?: illust.imageUrl) }
            }
        }.ifEmpty { listOf(illust.imageUrl) }
    }
    var selected by rememberSaveable(illust.id) { mutableIntStateOf(initialPageIndex.coerceIn(0, urls.lastIndex.coerceAtLeast(0))) }

    OverlayBottomSheet(
        show = true,
        title = stringResource(R.string.widget_illust_pick_page),
        onDismissRequest = onCancel,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PixivImage(
                url = urls[selected],
                contentDescription = illust.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(18.dp)),
                thumbnail = false,
                crossfade = true,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                itemsIndexed(urls) { index, url ->
                    Box(
                        modifier = Modifier
                            .height(72.dp)
                            .width(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                selected = index
                                onPageSelected(index)
                            },
                    ) {
                        PixivImage(
                            url = url,
                            contentDescription = "${illust.title} $index",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            thumbnail = true,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApply(selected) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.widget_illust_apply))
                }
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.widget_illust_cancel))
                }
            }
        }
    }
}
