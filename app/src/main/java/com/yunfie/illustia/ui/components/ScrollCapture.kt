package com.yunfie.illustia.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.CancellationSignal
import android.view.ScrollCaptureCallback
import android.view.ScrollCaptureSession
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalView
import java.util.function.Consumer

/**
 * 長いリスト・詳細画面を、システムの「スクロール キャプチャ」（長いスクリーンショット）に
 * 対応させるための共通 Modifier。
 *
 * `View.setScrollCaptureCallback` と `ScrollCaptureCallback` は API 31 (S) 以降にしか
 * 存在しないため、それ未満では何もせず素通しする。
 * Compose は主要なスクロール可能コンテナ（LazyColumn / LazyVerticalGrid など）に
 * `ScrollCaptureCallback` を自動で実装するが、`LazyColumn` を入れ子にした画面や
 * 独自のスクロール領域ではシステムが候補を発見できずキャプチャに失敗することがある。
 * ここでは画面ルートのビューに「受け皿」のコールバックを 1 度だけ登録して、
 * システムが候補を探す際に画面全体を 1 つのスクロール範囲として提示する。
 */
@Composable
internal fun Modifier.scrollCaptureEnabled(): Modifier {
    val view = LocalView.current
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        this
    } else {
        this.then(ScrollCaptureElement(view))
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private class ScrollCaptureElement(
    private val view: View,
) : ModifierNodeElement<ScrollCaptureNode>() {
    override fun create(): ScrollCaptureNode = ScrollCaptureNode(view)

    override fun update(node: ScrollCaptureNode) = Unit

    override fun equals(other: Any?): Boolean = other is ScrollCaptureElement && other.view === view

    override fun hashCode(): Int = System.identityHashCode(view)
}

@RequiresApi(Build.VERSION_CODES.S)
private class ScrollCaptureNode(
    private val view: View,
) : Modifier.Node() {
    override fun onAttach() {
        view.setScrollCaptureCallback(RootScrollCaptureCallback(view))
    }

    override fun onDetach() {
        view.setScrollCaptureCallback(null)
    }
}

/**
 * 画面ルート用のスクロールキャプチャコールバック。
 *
 * ビュー全体を 1 つのスクロール範囲として提示し、`onScrollCaptureImageRequest` で
 * 要求された矩形をビューのスナップショットから `ScrollCaptureSession` のサーフェスへ
 * 書き出す。Compose 側からスクロール位置を直接操作できないため、複数フレームにまたがる
 * 真正の「長いスクショ」は作れないが、単一フレーム分の画像を正しく返すことで
 * システム標準のキャプチャ（1 枚撮影へのフォールバック含む）が壊れないようにする。
 */
@RequiresApi(Build.VERSION_CODES.S)
private class RootScrollCaptureCallback(
    private val view: View,
) : ScrollCaptureCallback {
    private val locationOnScreen = IntArray(2)

    override fun onScrollCaptureSearch(
        signal: CancellationSignal,
        onReady: Consumer<Rect>,
    ) {
        onReady.accept(if (view.width > 0 && view.height > 0) Rect(0, 0, view.width, view.height) else Rect())
    }

    override fun onScrollCaptureStart(
        session: ScrollCaptureSession,
        signal: CancellationSignal,
        onReady: Runnable,
    ) = onReady.run()

    override fun onScrollCaptureImageRequest(
        session: ScrollCaptureSession,
        signal: CancellationSignal,
        captureArea: Rect,
        onComplete: Consumer<Rect>,
    ) {
        // captureArea はウィンドウ座標。ビュー内座標に直してスナップショットから切り出す。
        view.getLocationOnScreen(locationOnScreen)
        val viewLocation =
            Rect(
                locationOnScreen[0],
                locationOnScreen[1],
                locationOnScreen[0] + view.width,
                locationOnScreen[1] + view.height,
            )
        val visibleArea = Rect(captureArea)
        if (visibleArea.intersect(viewLocation)) {
            if (drawIntoSession(view, session, visibleArea)) {
                onComplete.accept(captureArea)
            } else {
                onComplete.accept(Rect())
            }
        } else {
            onComplete.accept(Rect())
        }
    }

    /** [bounds]（画面座標）の枠をセッションの surface へ描き込む。成功時 true。 */
    private fun drawIntoSession(
        view: View,
        session: ScrollCaptureSession,
        bounds: Rect,
    ): Boolean {
        val bitmap = view.captureWithinBounds(bounds, locationOnScreen)
        var drawn = false
        if (bitmap != null) {
            val canvas = session.surface.lockCanvas(null)
            if (canvas != null) {
                try {
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    drawn = true
                } finally {
                    session.surface.unlockCanvasAndPost(canvas)
                    bitmap.recycle()
                }
            } else {
                bitmap.recycle()
            }
        }
        return drawn
    }

    override fun onScrollCaptureEnd(onReady: Runnable) = onReady.run()

    /**
     * [bounds]（画面座標）の範囲をビューのスナップショットから切り出して返す。
     *
     * `PixelCopy` はウィンドウからしか取れないため、View 描画用途では
     * `View.draw(Canvas)` によるソフトウェア描画をフォールバックとして使う。
     */
    private fun View.captureWithinBounds(
        bounds: Rect,
        viewLocation: IntArray,
    ): Bitmap? {
        if (bounds.isEmpty) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // ビュー内座標系へ変換して切れ端を描く。
            canvas.translate(-(bounds.left - viewLocation[0]).toFloat(), -(bounds.top - viewLocation[1]).toFloat())
            draw(canvas)
            bitmap
        }.getOrNull()
    }
}
