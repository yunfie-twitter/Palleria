package com.yunfie.illustia

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

object DummyAppIconSwitcher {
    private const val ALIAS_REAL = "com.yunfie.illustia.MainActivityAlias"
    private const val ALIAS_DUMMY = "com.yunfie.illustia.MainActivityDummy"
    private const val ALIAS_CAT = "com.yunfie.illustia.MainActivityCat"

    fun apply(
        context: Context,
        privacyModeEnabled: Boolean,
        appIconVariant: String = "default",
    ) {
        val packageManager = context.packageManager
        try {
            val dummyState =
                if (privacyModeEnabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            val catState =
                if (!privacyModeEnabled && appIconVariant == "cat") {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
            val realState =
                if (!privacyModeEnabled && appIconVariant != "cat") {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }

            setComponentStateIfNeeded(
                packageManager = packageManager,
                componentName = ComponentName(context, ALIAS_REAL),
                desiredState = realState,
                enabledByDefault = true,
            )
            setComponentStateIfNeeded(
                packageManager = packageManager,
                componentName = ComponentName(context, ALIAS_DUMMY),
                desiredState = dummyState,
                enabledByDefault = false,
            )
            setComponentStateIfNeeded(
                packageManager = packageManager,
                componentName = ComponentName(context, ALIAS_CAT),
                desiredState = catState,
                enabledByDefault = false,
            )

            // Ensure the target activity itself is enabled
            setComponentStateIfNeeded(
                packageManager = packageManager,
                componentName = ComponentName(context, "com.yunfie.illustia.MainActivity"),
                desiredState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                enabledByDefault = true,
            )
        } catch (expectedFailure: RuntimeException) {
            Log.w("DummyAppIconSwitcher", "Failed to switch app icon alias", expectedFailure)
        }
    }

    private fun setComponentStateIfNeeded(
        packageManager: PackageManager,
        componentName: ComponentName,
        desiredState: Int,
        enabledByDefault: Boolean,
    ) {
        val currentState = packageManager.getComponentEnabledSetting(componentName)
        val currentlyEnabled =
            when (currentState) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true

                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED,
                -> false

                else -> enabledByDefault
            }
        val shouldBeEnabled = desiredState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        if (currentlyEnabled == shouldBeEnabled) return

        packageManager.setComponentEnabledSetting(
            componentName,
            desiredState,
            PackageManager.DONT_KILL_APP,
        )
    }
}
