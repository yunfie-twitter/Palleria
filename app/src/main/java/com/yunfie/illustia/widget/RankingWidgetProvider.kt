package com.yunfie.illustia.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RankingWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RankingGlanceWidget()

    companion object {
        const val ACTION_REFRESH_RANKING_WIDGET = "com.yunfie.illustia.widget.ACTION_REFRESH_RANKING_WIDGET"

        fun refreshAll(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                refreshAllSuspend(context)
            }
        }

        suspend fun refreshAllSuspend(context: Context) {
            RankingGlanceWidget().updateAll(context)
        }

        suspend fun publishPreview(context: Context) {
            RankingGlanceWidget().updateAll(context)
        }
    }
}
