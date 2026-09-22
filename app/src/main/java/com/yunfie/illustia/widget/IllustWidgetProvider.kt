package com.yunfie.illustia.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class IllustWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = IllustGlanceWidget()

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        val store = IllustWidgetStore(context)
        appWidgetIds.forEach { id ->
            store.load(id)?.imagePath?.let { path -> File(path).delete() }
            store.remove(id)
        }
    }

    companion object {
        const val ACTION_REFRESH_ILLUST_WIDGET = "com.yunfie.illustia.widget.ACTION_REFRESH_ILLUST_WIDGET"

        fun refreshAll(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                refreshAllSuspend(context)
            }
        }

        suspend fun refreshAllSuspend(context: Context) {
            IllustGlanceWidget().updateAll(context)
        }

        suspend fun publishPreview(context: Context) {
            IllustGlanceWidget().updateAll(context)
        }
    }
}
