package com.yunfie.illustia.ui

import androidx.compose.runtime.Composable
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.ui.app.IllustiaAppRoot

@Composable
fun IllustiaApp(viewModel: IllustiaViewModel) {
    IllustiaAppRoot(viewModel = viewModel)
}
