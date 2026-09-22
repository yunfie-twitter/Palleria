package com.yunfie.illustia.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class IllustGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val isPrivacyMode = SettingsStore(context.applicationContext).read().privacyModeEnabled
        val selection = if (isPrivacyMode) null else IllustWidgetStore(context).load(appWidgetId)
        val bitmap = loadWidgetBitmap(selection)
        val configIntent = buildConfigIntent(context, appWidgetId)
        val detailIntent = selection?.let { buildDetailIntent(context, it.illustId) }
        val promptText = resolvePromptText(context, isPrivacyMode, selection)

        provideContent {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null && detailIntent != null && selection != null) {
                    LoadedIllustView(
                        bitmap = bitmap,
                        title = selection.title,
                        detailIntent = detailIntent,
                    )
                } else {
                    EmptyIllustView(
                        promptText = promptText,
                        configIntent = configIntent,
                    )
                }
            }
        }
    }

    private suspend fun loadWidgetBitmap(selection: IllustWidgetSelection?): Bitmap? {
        if (selection == null) return null
        return withContext(Dispatchers.IO) {
            val file = File(selection.imagePath)
            if (file.exists()) decodeWidgetBitmap(file, WIDGET_IMAGE_MAX_DIMENSION) else null
        }
    }

    private fun resolvePromptText(
        context: Context,
        isPrivacyMode: Boolean,
        selection: IllustWidgetSelection?,
    ): String =
        when {
            isPrivacyMode -> context.getString(R.string.widget_illust_pick_prompt)
            selection != null -> context.getString(R.string.widget_illust_image_missing)
            else -> context.getString(R.string.widget_illust_pick_prompt)
        }

    private fun buildConfigIntent(
        context: Context,
        appWidgetId: Int,
    ): Intent =
        Intent(context, IllustWidgetConfigureActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun buildDetailIntent(
        context: Context,
        illustId: Long,
    ): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("pixiv://illusts/$illustId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    companion object {
        private const val WIDGET_IMAGE_MAX_DIMENSION = 960
        private const val COLOR_EMPTY_BACKGROUND = 0xFF1E212B
        private const val COLOR_CHIP_BACKGROUND = 0xFF2C3240

        fun decodeWidgetBitmap(
            file: File,
            maxDimension: Int,
        ): Bitmap? {
            val bounds =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension, maxDimension)
            val decoded =
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.RGB_565
                    },
                )

            return decoded?.let { scaleBitmap(it, maxDimension) }
        }

        private fun scaleBitmap(
            decoded: Bitmap,
            maxDimension: Int,
        ): Bitmap {
            if (decoded.width <= maxDimension && decoded.height <= maxDimension) {
                return decoded
            }
            val scale =
                minOf(
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

        private fun calculateInSampleSize(
            srcWidth: Int,
            srcHeight: Int,
            reqWidth: Int,
            reqHeight: Int,
        ): Int {
            var inSampleSize = 1
            var halfHeight = srcHeight / 2
            var halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
            return inSampleSize.coerceAtLeast(1)
        }
    }
}

@Composable
private fun LoadedIllustView(
    bitmap: Bitmap,
    title: String,
    detailIntent: Intent,
) {
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier =
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .clickable(actionStartActivity(detailIntent)),
    )
}

@Composable
private fun EmptyIllustView(
    promptText: String,
    configIntent: Intent,
) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFF1E212B)))
                .cornerRadius(24.dp)
                .clickable(actionStartActivity(configIntent))
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = promptText,
                style =
                    TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
            )
            Spacer(modifier = GlanceModifier.height(12.dp))
            Image(
                provider = ImageProvider(android.R.drawable.ic_menu_add),
                contentDescription = null,
                modifier =
                    GlanceModifier
                        .size(40.dp)
                        .background(ColorProvider(Color(0xFF2C3240)))
                        .cornerRadius(20.dp)
                        .padding(8.dp),
            )
        }
    }
}
