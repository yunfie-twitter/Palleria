package com.yunfie.illustia

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DummyAppIconSwitcherTest {
    @Test
    fun `splash follows privacy launcher without opening preferences`() {
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences = error("Splash selection must not read preferences")
            }
        try {
            DummyAppIconSwitcher.apply(context, privacyModeEnabled = true)
            DummyAppIconSwitcher.isPrivacyLauncherEnabled(context) shouldBe true
            DummyAppIconSwitcher.apply(context, privacyModeEnabled = false)
            DummyAppIconSwitcher.isPrivacyLauncherEnabled(context) shouldBe false
        } finally {
            DummyAppIconSwitcher.apply(context, privacyModeEnabled = false)
        }
    }
}
