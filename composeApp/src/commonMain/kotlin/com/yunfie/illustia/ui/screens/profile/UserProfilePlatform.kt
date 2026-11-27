package com.yunfie.illustia.ui.screens.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import top.yukonga.miuix.kmp.blur.LayerBackdrop

/** 編集ペンなどの Android drawable を共有 UI へ渡すための境界。 */
val LocalProfileEditIcon = compositionLocalOf<Painter?> { null }

/** ユーザーページ画面が Android 固有実装を必要とする操作・値。 */
interface UserProfilePlatform {
    /** ステータスバー/ナビゲーションバーの明暗を切り替える。 */
    @Composable
    fun SystemBarAppearance(
        lightStatusBars: Boolean,
        lightNavigationBars: Boolean,
        restoreLightStatusBars: Boolean,
        restoreLightNavigationBars: Boolean,
        transparentBarColors: Boolean = false,
    )

    /** Liquid Glass ボタン用の背景。無効時は null。 */
    @Composable
    fun rememberBackdrop(
        enabled: Boolean,
        backgroundColor: Color,
    ): LayerBackdrop?

    /** サムネイル画像の明度からヘッダーのアイコン色を決める。未対応環境では false。 */
    suspend fun resolveDarkHeaderIcons(
        thumbnailUrl: String,
        proxyBaseUrl: String,
    ): Boolean = false

    /** うごイラのフレーム再生。デコード実装はプラットフォーム側が提供する。 */
    @Composable
    fun UgoiraPlayer(
        previewUrl: String,
        contentDescription: String?,
        loadPlayback: suspend () -> UgoiraPlayback,
        powerSaveEnabled: Boolean,
        isActive: Boolean,
        onTap: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Box(modifier)
    }

    /** 全画面表示の切り替え。システムバーの表示制御はプラットフォーム側が持つ。 */
    @Composable
    fun FullscreenEffect(
        enabled: Boolean,
        darkTheme: Boolean,
        transparentNavigationBar: Boolean = true,
    ) = Unit

    /**
     * キーボードショートカットを解釈する。
     * ビューアは F の全画面切り替えと Ctrl+S の保存を扱い、未対応環境では null。
     */
    fun viewerShortcut(keyEvent: Any): ViewerShortcut? = null

    /** ビューアのページ送り方向 (-1/0/1) を解決する。未対応環境では 0。 */
    fun readerPageDirection(
        keyEvent: Any,
        volumeKeysEnabled: Boolean,
    ): Int = 0

    /** ビューア用のうごイラ再生（ズームとタップ連動）。未対応環境では静止画のみ。 */
    @Composable
    fun ViewerUgoira(
        previewUrl: String,
        contentDescription: String,
        loadPlayback: suspend () -> UgoiraPlayback,
        powerSaveEnabled: Boolean,
        onZoomChanged: (Boolean) -> Unit,
        onTap: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Box(modifier)
    }

    /** リーダーなど画面ごとのシステムバー明暗切り替え（色は触らない）。 */
    @Composable
    fun ReaderSystemBars(
        lightStatusBars: Boolean,
        lightNavigationBars: Boolean,
        restoreLightStatusBars: Boolean,
        restoreLightNavigationBars: Boolean,
    ) = Unit
}

enum class ViewerShortcut { ToggleFullscreen, SaveCurrentPage }
