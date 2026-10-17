package com.yunfie.illustia.pallasync

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.screens.pallasync.PallaSyncRecoverySetCapacity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Presentation-only public evidence: signature and physical holder tests are separate. */
@RunWith(AndroidJUnit4::class)
class PallaSyncRecoveryStatusUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun entry(usable: Int) =
        PallaSyncRecoverySetStore.Entry(
            """{"recovery_set_id":"11111111-1111-4111-8111-111111111111","threshold":2}""",
            """[{"state":"active"}]""",
            List(3) { index ->
                PallaSyncRecoverySetStore.HolderState("public-$index", "public-holder-$index", "public-hash", index < usable)
            },
        )

    @Test
    fun oneShareIsUnavailableAndExactlyThresholdWarnsOfTheNextLoss() {
        var evidence by mutableStateOf(entry(1))
        compose.setContent {
            MiuixTheme {
                Scaffold {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        PallaSyncRecoverySetCapacity(evidence, false, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.pallasync_recovery_unavailable)).assertIsDisplayed()
        compose.runOnIdle { evidence = entry(2) }
        compose.onNodeWithText(compose.activity.getString(R.string.pallasync_recovery_next_loss)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.pallasync_recovery_unavailable)).assertDoesNotExist()
        compose.runOnIdle { evidence = entry(3) }
        compose.onNodeWithText(compose.activity.getString(R.string.pallasync_recovery_margin)).assertIsDisplayed()
        compose.runOnIdle { evidence = entry(3).copy(statusesJson = """[{"state":"retired"}]""", interrupted = true) }
        compose.onNodeWithText(compose.activity.getString(R.string.pallasync_recovery_retired)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.pallasync_recovery_incomplete)).assertDoesNotExist()
    }

    @Test
    fun lostAndLeakButtonsRequireAnExplicitActionAndAreDisabledDuringAnOperation() {
        var busy by mutableStateOf(true)
        var selected: String? = null
        compose.setContent {
            MiuixTheme {
                Scaffold {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        PallaSyncRecoverySetCapacity(entry(3), busy, { selected = it }, { selected = "leak" })
                    }
                }
            }
        }
        val label = compose.activity.getString(R.string.pallasync_recovery_mark_lost, 1)
        compose.onNodeWithText(label).performScrollTo().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals(null, selected)
            busy = false
        }
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("public-0", selected) }
        compose.onNodeWithText(compose.activity.getString(R.string.pallasync_recovery_leak)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("leak", selected) }
    }
}
