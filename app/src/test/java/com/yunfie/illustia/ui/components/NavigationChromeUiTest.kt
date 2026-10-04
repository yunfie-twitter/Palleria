package com.yunfie.illustia.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NavigationChromeUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun iconOnlyFlagHidesTextButKeepsAccessibleClickableTabs() {
        val settings = mutableStateOf(AppSettings())
        var clicks = 0
        compose.setContent {
            MiuixTheme {
                AppNavigationBar(settings.value) {
                    NavigationBarItem(false, { clicks++ }, MiuixIcons.Search, "Search")
                }
            }
        }
        compose.onNodeWithText("Search").assertExists()
        compose.runOnIdle {
            settings.value = settings.value.copy(featureFlags = mapOf(FeatureFlag.NavigationIconsOnly.key to true))
        }
        compose.onNodeWithText("Search").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun systemButtonInsetsAreReservedExactlyOnceAtBottomAndSide() {
        val side = mutableStateOf(false)
        compose.setContent {
            val insets = if (side.value) WindowInsets(right = 48.dp) else WindowInsets(bottom = 48.dp)
            Box(Modifier.size(300.dp).appNavigationSafeArea(insets)) {
                Box(Modifier.fillMaxSize().appNavigationSafeArea(insets).testTag("safe"))
            }
        }
        compose.onNodeWithTag("safe").assertHeightIsEqualTo(252.dp).assertWidthIsEqualTo(300.dp)
        compose.runOnIdle { side.value = true }
        compose.onNodeWithTag("safe").assertHeightIsEqualTo(300.dp).assertWidthIsEqualTo(252.dp)
    }

    @Test
    fun fallbackBlurHeaderDoesNotCoverInitialItemsAndStaysVisibleWhenScrolled() {
        compose.setContent {
            MiuixTheme {
                ScrollBlurScaffold(
                    enabled = true,
                    scrollBehavior = MiuixScrollBehavior(),
                    modifier = Modifier.size(300.dp),
                    header = { Box(Modifier.height(64.dp).testTag("header")) },
                ) {
                    LazyColumn(
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(top = LocalScrollHeaderInset.current),
                        modifier = Modifier.fillMaxSize().testTag("list"),
                    ) {
                        items((0..30).toList()) { Text("Item $it", Modifier.height(48.dp)) }
                    }
                }
            }
        }
        compose.onNodeWithText("Item 0").assertTopPositionInRootIsEqualTo(64.dp)
        compose.onNodeWithTag("list").performScrollToIndex(5)
        compose.onNodeWithTag("header").assertTopPositionInRootIsEqualTo(0.dp).assertHeightIsEqualTo(64.dp)
    }
}
