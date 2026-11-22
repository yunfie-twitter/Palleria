package com.yunfie.illustia

import android.app.Activity
import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemBarConfigurationTest {
    @Test
    fun navigationBarContrastScrimIsDisabledOnAndroidQAndLater() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        disableNavigationBarContrastEnforcement(activity.window)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertFalse(activity.window.isNavigationBarContrastEnforced)
        }
    }
}
