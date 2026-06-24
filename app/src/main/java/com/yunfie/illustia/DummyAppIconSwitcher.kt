package com.yunfie.illustia

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object DummyAppIconSwitcher {
    private const val ALIAS_REAL = "com.yunfie.illustia.MainActivity"
    private const val ALIAS_DUMMY = "com.yunfie.illustia.MainActivityDummy"

    fun apply(context: Context, privacyModeEnabled: Boolean) {
        val packageManager = context.packageManager
        try {
            val enable = if (privacyModeEnabled) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            val disable = if (privacyModeEnabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            packageManager.setComponentEnabledSetting(
                ComponentName(context, ALIAS_REAL),
                enable,
                PackageManager.DONT_KILL_APP,
            )
            packageManager.setComponentEnabledSetting(
                ComponentName(context, ALIAS_DUMMY),
                disable,
                PackageManager.DONT_KILL_APP,
            )
        } catch (error: Throwable) {
            Log.w("DummyAppIconSwitcher", "Failed to switch app icon alias", error)
        }
    }
}
