package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PallaSyncAdvancedMaintenanceToggleTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun advancedMaintenanceCanBeExpandedAndHidden() {
        val expanded = mutableStateOf(false)
        val context = ApplicationProvider.getApplicationContext<Application>()
        compose.setContent {
            MiuixTheme {
                PallaSyncAdvancedMaintenanceToggle(expanded.value) { expanded.value = !expanded.value }
            }
        }

        compose
            .onNodeWithText(
                context.getString(R.string.pallasync_advanced_show),
            ).assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        compose.onNodeWithText(context.getString(R.string.pallasync_advanced_hide)).assertIsDisplayed()
    }
}
