package com.yunfie.illustia.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViews.RemoteResponse
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.R
import java.io.File

class IllustWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        refreshAll(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = IllustWidgetStore(context)
        appWidgetIds.forEach { id ->
            store.load(id)?.imagePath?.let { path -> File(path).delete() }
            store.remove(id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_ILLUST_WIDGET -> refreshAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_ILLUST_WIDGET = "com.yunfie.illustia.widget.ACTION_REFRESH_ILLUST_WIDGET"
        private const val WIDGET_IMAGE_MAX_DIMENSION = 960

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, IllustWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.illust_widget)
            runCatching {
                val store = IllustWidgetStore(context)
                val selection = store.load(appWidgetId)
                if (selection == null) {
                    bindEmpty(context, views, appWidgetId)
                } else {
                    bindSelection(context, views, appWidgetId, selection)
                }
            }.onFailure {
                bindEmpty(context, views, appWidgetId)
            }
            runCatching {
                manager.updateAppWidget(appWidgetId, views)
            }.onFailure {
                val fallback = RemoteViews(context.packageName, R.layout.illust_widget)
                bindEmpty(context, fallback, appWidgetId)
                manager.updateAppWidget(appWidgetId, fallback)
            }
        }

        private fun bindEmpty(context: Context, views: RemoteViews, appWidgetId: Int) {
            views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
            views.setViewVisibility(R.id.widget_content, View.GONE)
            views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_illust_pick_prompt))
            views.setImageViewResource(R.id.widget_empty_button, android.R.drawable.ic_menu_add)
            views.setOnClickPendingIntent(R.id.widget_empty_button, configurePendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_root, configurePendingIntent(context, appWidgetId))
        }

        private fun bindSelection(context: Context, views: RemoteViews, appWidgetId: Int, selection: IllustWidgetSelection) {
            views.setViewVisibility(R.id.widget_empty_state, View.GONE)
            views.setViewVisibility(R.id.widget_content, View.VISIBLE)
            views.setTextViewText(R.id.widget_title, selection.title.ifBlank { context.getString(R.string.widget_illust_title) })
            views.setOnClickPendingIntent(R.id.widget_change, configurePendingIntent(context, appWidgetId))
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))
            views.setImageViewResource(R.id.widget_change, android.R.drawable.ic_menu_edit)
            views.setImageViewResource(R.id.widget_refresh, android.R.drawable.ic_popup_sync)

            val imageFile = File(selection.imagePath)
            if (imageFile.exists()) {
                val bitmap = decodeWidgetBitmap(imageFile, WIDGET_IMAGE_MAX_DIMENSION)
                if (bitmap != null) {
                    views.setImageViewBitmap(R.id.widget_image, bitmap)
                    views.setOnClickResponse(
                        R.id.widget_root,
                        RemoteResponse.fromPendingIntent(detailPendingIntent(context, selection.illustId))
                            .addSharedElement(R.id.widget_image, "illust_widget_image"),
                    )
                } else {
                    views.setImageViewResource(R.id.widget_image, R.mipmap.ic_launcher)
                    views.setOnClickPendingIntent(R.id.widget_root, configurePendingIntent(context, appWidgetId))
                }
            } else {
                views.setImageViewResource(R.id.widget_image, R.mipmap.ic_launcher)
                views.setViewVisibility(R.id.widget_empty_state, View.VISIBLE)
                views.setViewVisibility(R.id.widget_content, View.GONE)
                views.setTextViewText(R.id.widget_empty_title, context.getString(R.string.widget_illust_image_missing))
                views.setImageViewResource(R.id.widget_empty_button, android.R.drawable.ic_menu_add)
                views.setOnClickPendingIntent(R.id.widget_empty_button, configurePendingIntent(context, appWidgetId))
                views.setOnClickPendingIntent(R.id.widget_root, configurePendingIntent(context, appWidgetId))
            }
        }

        private fun configurePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, IllustWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun refreshPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, IllustWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_ILLUST_WIDGET
            }
            return PendingIntent.getBroadcast(
                context,
                2201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun detailPendingIntent(context: Context, illustId: Long): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("pixiv://illusts/$illustId")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context,
                (illustId xor (illustId ushr 32)).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
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
            val targetWidth = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (decoded.height * scale).toInt().coerceAtLeast(1)
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
    }
}
