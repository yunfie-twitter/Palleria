package com.yunfie.illustia.platform

import com.yunfie.illustia.GlitchTipTelemetry
import com.yunfie.illustia.IllustiaApplication

/**
 * [TelemetryControl] の Android 実装。
 *
 * アプリケーション経由でクラッシュレポート送信の有効/無効を切り替える。
 */
class AndroidTelemetryControl(
    private val application: IllustiaApplication,
) : TelemetryControl {
    override fun setEnabled(enabled: Boolean) {
        GlitchTipTelemetry.setEnabled(application.applicationContext, enabled)
    }
}
