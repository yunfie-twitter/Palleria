package com.yunfie.illustia.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Shared-element wiring for the artwork card to detail transition.
 *
 * The two halves of a shared element live in different Navigation3 entries, so the
 * [SharedTransitionScope] comes from around the nav host while the [AnimatedVisibilityScope]
 * belongs to whichever entry is currently composing. Both are carried in composition locals so
 * grids and cards stay unaware of the feature and keep their existing signatures. Everything is
 * null when the feature is off, and [sharedArtworkElement] then returns the modifier untouched,
 * which leaves the default transition exactly as before.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal val LocalNavSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Supplied per Navigation3 entry, because the animated visibility scope exists only inside the
 * entry that Navigation3 is currently animating.
 */
internal val LocalNavEntryAnimatedVisibilityScope =
    compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * True for rows that must keep the previous slide animation, such as the related artworks under a
 * detail page. Those cards open a page that already shows that artwork, so growing a small row into
 * the hero image would read as a different move from the rest of the page.
 */
internal val LocalSuppressSharedArtworkTransition = compositionLocalOf { false }

/**
 * The key both the card thumbnail and the detail image register under. Keying on the artwork id
 * rather than a composable instance is what lets the two screens match up.
 */
internal fun sharedArtworkKey(illustId: Long): String = "artwork-image-$illustId"

/**
 * Marks this element as the artwork image for [illustId]. Returns the modifier unchanged when the
 * feature is off or suppressed, so callers need no branching of their own.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.sharedArtworkElement(illustId: Long): Modifier {
    val scope = LocalNavSharedTransitionScope.current ?: return this
    val visibilityScope = LocalNavEntryAnimatedVisibilityScope.current ?: return this
    if (LocalSuppressSharedArtworkTransition.current) return this
    return with(scope) {
        sharedElement(
            sharedContentState = rememberSharedContentState(key = sharedArtworkKey(illustId)),
            animatedVisibilityScope = visibilityScope,
        )
    }
}

/**
 * Publishes the current entry's animated visibility scope so cards and the detail header compiled
 * inside that entry can join the transition.
 */
@Composable
internal fun ProvideEntrySharedTransitionScope(content: @Composable () -> Unit) {
    val visibilityScope = androidx.navigation3.ui.LocalNavAnimatedContentScope.current
    CompositionLocalProvider(LocalNavEntryAnimatedVisibilityScope provides visibilityScope) {
        content()
    }
}
