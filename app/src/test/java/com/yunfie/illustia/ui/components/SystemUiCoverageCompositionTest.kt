package com.yunfie.illustia.ui.components

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SystemUiCoverageCompositionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun coverageChangesRecomposeOnlyConsumers() {
        val counts = CompositionCounts()
        val covered = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalSystemUiCovered provides covered.value) {
                CoverageConsumer(counts)
                CoverageIndependentContent(counts)
            }
        }
        compose.waitForIdle()
        val initialConsumerRuns = counts.consumerRuns
        val initialIndependentRuns = counts.independentRuns

        compose.runOnIdle { covered.value = true }
        compose.waitForIdle()

        assertTrue(counts.consumerRuns > initialConsumerRuns)
        assertEquals(initialIndependentRuns, counts.independentRuns)
    }

    @Composable
    private fun CoverageConsumer(counts: CompositionCounts) {
        LocalSystemUiCovered.current
        SideEffect { counts.consumerRuns++ }
    }

    @Composable
    private fun CoverageIndependentContent(counts: CompositionCounts) {
        SideEffect { counts.independentRuns++ }
    }

    @Stable
    private class CompositionCounts {
        var consumerRuns = 0
        var independentRuns = 0
    }
}
