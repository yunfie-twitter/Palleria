package com.yunfie.illustia.platform

import android.content.Context
import com.yunfie.illustia.DummyAppIconSwitcher

/**
 * [AppIconSwitcher] の Android 実装。
 *
 * `Context` を保持し、既存の [DummyAppIconSwitcher.apply] へ委譲する。
 */
class AndroidAppIconSwitcher(
    private val context: Context,
) : AppIconSwitcher {
    override fun apply(
        privacyModeEnabled: Boolean,
        appIconVariant: String,
    ) {
        DummyAppIconSwitcher.apply(context, privacyModeEnabled, appIconVariant)
    }
}
