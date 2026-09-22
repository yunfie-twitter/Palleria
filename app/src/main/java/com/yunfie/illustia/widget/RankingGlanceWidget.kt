package com.yunfie.illustia.widget

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
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yunfie.illustia.IllustiaApplication
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.R
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.Request

class RankingGlanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val settings = SettingsStore(context.applicationContext).read()
        val state = fetchRankingState(context, settings)

        provideContent {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(COLOR_BG)))
                        .cornerRadius(24.dp)
                        .padding(14.dp),
            ) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    RankingHeader(context = context)
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    when (state) {
                        is RankingWidgetState.LoggedOut -> LoggedOutView(context = context)
                        is RankingWidgetState.Error -> ErrorView(context = context)
                        is RankingWidgetState.Success -> SuccessView(context = context, entries = state.entries)
                    }
                }
            }
        }
    }

    private suspend fun fetchRankingState(
        context: Context,
        settings: AppSettings,
    ): RankingWidgetState {
        val loggedIn = settings.refreshToken.isNotBlank() && !settings.privacyModeEnabled
        if (!loggedIn) return RankingWidgetState.LoggedOut

        return withContext(Dispatchers.IO) {
            val repository = IllustiaRepository(SettingsStore(context.applicationContext))
            runCatching {
                repository.login(settings.refreshToken)
                val page = repository.loadRanking("day")
                val entries =
                    coroutineScope {
                        page.items
                            .take(MAX_RANKING_ITEMS)
                            .mapIndexed { index, illust ->
                                async {
                                    val bitmap = loadThumbnail(context, illust)
                                    RankingWidgetEntry(
                                        rank = index + 1,
                                        id = illust.id,
                                        title = illust.title.ifBlank { context.getString(R.string.widget_ranking_title) },
                                        artistName = illust.artistName.ifBlank { "Pixiv" },
                                        bitmap = bitmap,
                                    )
                                }
                            }.map { it.await() }
                    }
                RankingWidgetState.Success(entries)
            }.getOrElse {
                RankingWidgetState.Error
            }
        }
    }

    private suspend fun loadThumbnail(
        context: Context,
        illust: Illust,
    ): Bitmap? {
        val proxyBaseUrl = SettingsStore(context.applicationContext).read().pixivImageProxyBaseUrl
        val imageUrl = proxyPixivImageUrl(illust.thumbnailUrl, proxyBaseUrl)
        val request =
            Request
                .Builder()
                .url(imageUrl)
                .headers(
                    Headers
                        .Builder()
                        .add("Referer", "https://www.pixiv.net/")
                        .add("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Illustia)")
                        .build(),
                ).build()
        return runCatching {
            val app = context.applicationContext as? IllustiaApplication ?: return@runCatching null
            val response = app.sharedHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body.byteStream().use { stream -> BitmapFactory.decodeStream(stream) }
            }
        }.getOrNull()
    }

    companion object {
        private const val MAX_RANKING_ITEMS = 3
        private const val COLOR_BG = 0xFF16181F
    }
}

@Composable
private fun RankingHeader(context: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_ranking_title),
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFF5F7FA)),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = context.getString(R.string.widget_ranking_refresh),
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFE8ECF4)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            modifier =
                GlanceModifier
                    .background(ColorProvider(Color(0xFF20242F)))
                    .cornerRadius(12.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .clickable(actionRunCallback<RefreshRankingAction>()),
        )
    }
}

@Composable
private fun ColumnScope.LoggedOutView(context: Context) {
    Column(
        modifier = GlanceModifier.fillMaxSize().defaultWeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_ranking_login_title),
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFF5F7FA)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_ranking_login_subtitle),
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFAAB2C0)),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
        )
        Spacer(modifier = GlanceModifier.height(12.dp))
        val mainIntent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        Text(
            text = context.getString(R.string.widget_ranking_login_button),
            style =
                TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            modifier =
                GlanceModifier
                    .background(ColorProvider(Color(0xFF242834)))
                    .cornerRadius(12.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(actionStartActivity(mainIntent)),
        )
    }
}

@Composable
private fun ColumnScope.ErrorView(context: Context) {
    Column(
        modifier = GlanceModifier.fillMaxSize().defaultWeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = context.getString(R.string.widget_ranking_error),
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFF5F7FA)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_ranking_login_subtitle),
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFAAB2C0)),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
        )
        Spacer(modifier = GlanceModifier.height(12.dp))
        Text(
            text = context.getString(R.string.widget_ranking_refresh),
            style =
                TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            modifier =
                GlanceModifier
                    .background(ColorProvider(Color(0xFF242834)))
                    .cornerRadius(12.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable(actionRunCallback<RefreshRankingAction>()),
        )
    }
}

@Composable
private fun ColumnScope.SuccessView(
    context: Context,
    entries: List<RankingWidgetEntry>,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().defaultWeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            RankingItemRow(context = context, entry = entry)
        }
    }
}

@Composable
private fun ColumnScope.RankingItemRow(
    context: Context,
    entry: RankingWidgetEntry,
) {
    val artworkIntent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("https://www.pixiv.net/artworks/${entry.id}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .padding(vertical = 3.dp)
                .background(ColorProvider(Color(0xFF20242F)))
                .cornerRadius(12.dp)
                .padding(6.dp)
                .clickable(actionStartActivity(artworkIntent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.rank.toString(),
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFF9AA4B4)),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            modifier = GlanceModifier.width(28.dp),
        )
        if (entry.bitmap != null) {
            Image(
                provider = ImageProvider(entry.bitmap),
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier =
                    GlanceModifier
                        .size(44.dp)
                        .cornerRadius(8.dp),
            )
        } else {
            Box(
                modifier =
                    GlanceModifier
                        .size(44.dp)
                        .background(ColorProvider(Color(0xFF2D333F)))
                        .cornerRadius(8.dp),
            ) {}
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        RankingItemText(title = entry.title, artistName = entry.artistName)
    }
}

@Composable
private fun RowScope.RankingItemText(
    title: String,
    artistName: String,
) {
    Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
            text = title,
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFF5F7FA)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
        Text(
            text = artistName,
            style =
                TextStyle(
                    color = ColorProvider(Color(0xFFAAB2C0)),
                    fontSize = 10.sp,
                ),
            maxLines = 1,
        )
    }
}

class RefreshRankingAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        RankingGlanceWidget().update(context, glanceId)
    }
}

private sealed interface RankingWidgetState {
    data object LoggedOut : RankingWidgetState

    data object Error : RankingWidgetState

    data class Success(
        val entries: List<RankingWidgetEntry>,
    ) : RankingWidgetState
}

private data class RankingWidgetEntry(
    val rank: Int,
    val id: Long,
    val title: String,
    val artistName: String,
    val bitmap: Bitmap?,
)
