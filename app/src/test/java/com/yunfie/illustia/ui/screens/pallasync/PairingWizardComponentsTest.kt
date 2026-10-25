package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PairingWizardComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `stepper renders one dot per pairing step`() {
        compose.setContent { MiuixTheme { PairingStepper(current = PairingStep.SHOW_CODE) } }

        compose.onNodeWithTag("pallasync-pairing-stepper").assertIsDisplayed()
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    /** SAS digits are the anti-MITM check, so they must be rendered and grouped, not truncated. */
    @Test
    fun `sas code is rendered in full`() {
        compose.setContent { MiuixTheme { PairingSasCode(sas = "ABCDEFGHIJKL") } }

        compose.onNodeWithTag("pallasync-pairing-sas").assertIsDisplayed()
        compose.onNodeWithText("ABCD").assertIsDisplayed()
        compose.onNodeWithText("EFGH").assertIsDisplayed()
        compose.onNodeWithText("IJKL").assertIsDisplayed()
    }

    @Test
    fun `role card shows its title and description`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.setContent {
            MiuixTheme {
                PairingRoleCard(
                    title = context.getString(R.string.pallasync_role_add_device),
                    description = context.getString(R.string.pallasync_role_add_device_description),
                    icon = MiuixIcons.Add,
                    selected = false,
                    enabled = true,
                    onClick = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.pallasync_role_add_device)).assertIsDisplayed()
        compose
            .onNodeWithText(context.getString(R.string.pallasync_role_add_device_description))
            .assertIsDisplayed()
    }
}
