package com.yunfie.illustia.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Static zero-cost modifier for grid item placement, avoiding layout animations and recomposition overhead during scroll. */
@Composable
fun LazyGridItemScope.animatedGridPlacement(): Modifier = Modifier
