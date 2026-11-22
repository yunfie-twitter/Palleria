package com.yunfie.illustia.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class GridPlacementUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun columnChangeUpdatesCardPositionsDirectlyWithoutAnimationLag() {
        val columns = mutableIntStateOf(2)
        compose.setContent {
            LazyVerticalGrid(columns = GridCells.Fixed(columns.intValue), modifier = Modifier.width(300.dp).height(300.dp)) {
                items(6, key = { it }) {
                    Box(animatedGridPlacement().height(80.dp).testTag("card$it"))
                }
            }
        }
        val initial =
            compose
                .onNodeWithTag("card1")
                .getUnclippedBoundsInRoot()
                .left.value
        assertEquals(150f, initial, 1f)

        compose.runOnIdle { columns.intValue = 3 }
        compose.waitForIdle()

        val final =
            compose
                .onNodeWithTag("card1")
                .getUnclippedBoundsInRoot()
                .left.value
        assertEquals(100f, final, 1f)
    }
}
