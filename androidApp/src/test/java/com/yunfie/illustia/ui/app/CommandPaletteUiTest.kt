package com.yunfie.illustia.ui.app

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class CommandPaletteUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enterExecutesTypedSearchOnce() {
        val calls = mutableListOf<Pair<PaletteAction, String>>()
        compose.setContent { MiuixTheme { CommandPalette({}, { action, query -> calls.add(action to query) }) } }
        compose.onNode(hasSetTextAction()).performTextInput("unique artwork query")
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(listOf(PaletteAction.Search to "unique artwork query"), calls) }
    }

    @Test fun arrowsSelectCommandsAndEscapeDismisses() {
        var selected: PaletteAction? = null
        var dismissed = false
        compose.setContent { MiuixTheme { CommandPalette({ dismissed = true }, { action, _ -> selected = action }) } }
        compose.onNode(hasSetTextAction()).performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        compose.runOnIdle { assertEquals(PaletteAction.Ranking, selected) }
        compose.onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        compose.runOnIdle { assertTrue(dismissed) }
    }
}
