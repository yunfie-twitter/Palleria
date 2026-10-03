package com.yunfie.illustia.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.runComposition
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class IllustWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun storeObservesSelectionChangesFromAnotherInstance() =
        runBlocking {
            val store = IllustWidgetStore(context)
            val expected = selection(imageFile(), "updated")
            val waiting = async(Dispatchers.Default) { store.observe(41).first { it != null } }
            IllustWidgetStore(context).save(41, expected)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(expected, withTimeout(10_000) { waiting.await() })
            store.remove(41)
            assertEquals(null, store.observe(41).first())
        }

    @OptIn(ExperimentalGlanceApi::class)
    @Test
    fun activeWidgetCompositionDisplaysImageSavedAfterItsEmptyState() =
        runBlocking {
            val id =
                checkNotNull(
                    GlanceAppWidgetManager(context).getGlanceIdBy(
                        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 42),
                    ),
                )
            val results = Channel<RemoteViews>(Channel.UNLIMITED)
            val session =
                launch(Dispatchers.Default) {
                    IllustGlanceWidget()
                        .runComposition(context, id, sizes = listOf(DpSize(240.dp, 240.dp)))
                        .collect { results.send(it) }
                }
            try {
                withTimeout(20_000) { results.receive() }
                IllustWidgetStore(context).save(42, selection(imageFile(), "selected artwork"))
                shadowOf(Looper.getMainLooper()).idle()
                val image =
                    withTimeout(20_000) {
                        var found: ImageView? = null
                        while (found == null) {
                            val view = results.receive().apply(context, FrameLayout(context))
                            found = images(view).firstOrNull { it.contentDescription == "selected artwork" }
                        }
                        found
                    }
                assertNotNull(image.drawable)
                IllustWidgetStore(context).save(42, selection(imageFile(32, 32), "selected artwork"))
                shadowOf(Looper.getMainLooper()).idle()
                withTimeout(20_000) {
                    var replaced = false
                    while (!replaced) {
                        val view = results.receive().apply(context, FrameLayout(context))
                        replaced =
                            images(view).any {
                                it.contentDescription == "selected artwork" &&
                                    (it.drawable as? BitmapDrawable)?.bitmap?.width == 32
                            }
                    }
                }
            } finally {
                session.cancelAndJoin()
                results.close()
            }
        }

    @Test
    fun decodeBoundsBothLandscapeAndPortraitImages() {
        listOf(4000 to 40, 40 to 4000).forEach { (width, height) ->
            val file = imageFile(width, height)
            val decoded = checkNotNull(IllustGlanceWidget.decodeWidgetBitmap(file, 320))
            assertTrue(decoded.width <= 320)
            assertTrue(decoded.height <= 320)
            decoded.recycle()
            file.delete()
        }
    }

    private fun imageFile(
        width: Int = 64,
        height: Int = 64,
    ): File {
        val file = File.createTempFile("widget-test", ".png", context.filesDir)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun selection(
        file: File,
        title: String,
    ) = IllustWidgetSelection(1L, 0, 1, title, "artist", "https://example.invalid/image.png", file.absolutePath)

    private fun images(view: View): List<ImageView> =
        when (view) {
            is ImageView -> listOf(view)
            is ViewGroup -> (0 until view.childCount).flatMap { images(view.getChildAt(it)) }
            else -> emptyList()
        }
}
