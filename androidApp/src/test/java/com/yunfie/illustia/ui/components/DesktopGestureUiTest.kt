package com.yunfie.illustia.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DesktopGestureUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun heartTapAndPrivateLongPressReachTheirCallbacksExactlyOnce() {
        var clicks = 0
        var privateClicks = 0
        compose.setContent {
            MiuixTheme {
                BookmarkHeartButton(false, { clicks++ }, Modifier.testTag("heart"), onLongClick = { privateClicks++ })
            }
        }
        compose.onNodeWithTag("heart").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(0, privateClicks)
        }
        compose.onNodeWithTag("heart").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, clicks)
            assertEquals(1, privateClicks)
        }
    }

    @Test
    fun slowPinchCancelsQuickPeekButSingleFingerLongPressStillWorks() {
        var peeks = 0
        var clicks = 0
        compose.setContent {
            MiuixTheme {
                Box(Modifier.size(250.dp).testTag("grid").pinchToChangeColumns(true, 3) {}) {
                    Card(modifier = Modifier.fillMaxSize(), onClick = { clicks++ }, onLongPress = { peeks++ }) {}
                }
            }
        }
        compose.onNodeWithTag("grid").performTouchInput {
            down(0, Offset(60f, 60f))
            advanceEventTime(50)
            down(1, Offset(180f, 180f))
            advanceEventTime(800)
            move()
            up(1)
            advanceEventTime(800)
            move()
            up(0)
        }
        compose.runOnIdle {
            assertEquals(0, peeks)
            assertEquals(0, clicks)
        }
        compose.onNodeWithTag("grid").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, peeks)
            assertEquals(0, clicks)
        }
    }
}
