package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class ArtworkCardPreferences(
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showTags: Boolean = false,
    val showBookmarkCount: Boolean = false,
    val showAiBadge: Boolean = true,
    val showR18Badge: Boolean = true,
    val doubleTapToBookmark: Boolean = false,
)

val LocalArtworkCardPreferences = staticCompositionLocalOf { ArtworkCardPreferences() }
