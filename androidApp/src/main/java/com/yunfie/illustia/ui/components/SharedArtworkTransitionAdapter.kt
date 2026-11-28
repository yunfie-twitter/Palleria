package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * Publishes the current entry's animated visibility scope so cards and the detail header compiled
 * inside that entry can join the transition.
 *
 * Navigation3 is Android-only here, so this half of [LocalNavEntryAnimatedVisibilityScope] stays in
 * androidApp while the rest of the shared-element wiring lives in composeApp commonMain.
 */
@Composable
fun ProvideEntrySharedTransitionScope(content: @Composable () -> Unit) {
    val visibilityScope = LocalNavAnimatedContentScope.current
    CompositionLocalProvider(LocalNavEntryAnimatedVisibilityScope provides visibilityScope) {
        content()
    }
}
