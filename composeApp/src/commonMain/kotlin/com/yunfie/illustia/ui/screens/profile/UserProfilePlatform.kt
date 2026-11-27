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
}
