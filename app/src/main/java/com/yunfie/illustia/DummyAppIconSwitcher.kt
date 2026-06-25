package com.yunfie.illustia

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object DummyAppIconSwitcher {
    private const val ALIAS_REAL = "com.yunfie.illustia.MainActivityAlias"
    private const val ALIAS_DUMMY = "com.yunfie.illustia.MainActivityDummy"

    fun apply(context: Context, privacyModeEnabled: Boolean) {
        val packageManager = context.packageManager
        try {
            val realState = if (privacyModeEnabled) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            val dummyState = if (privacyModeEnabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            packageManager.setComponentEnabledSetting(
                ComponentName(context, ALIAS_REAL),
                realState,
                PackageManager.DONT_KILL_APP,
            )
            packageManager.setComponentEnabledSetting(
                ComponentName(context, ALIAS_DUMMY),
                dummyState,
                PackageManager.DONT_KILL_APP,
            )
            
            // Ensure the target activity itself is enabled
            packageManager.setComponentEnabledSetting(
                ComponentName(context, "com.yunfie.illustia.MainActivity"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        } catch (error: Throwable) {
            Log.w("DummyAppIconSwitcher", "Failed to switch app icon alias", error)
        }
    }
}
