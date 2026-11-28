package com.yunfie.illustia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.yunfie.illustia.platform.ComposeStringProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS アプリのルート画面。
 *
 * 本編のナビゲーション（`IllustiaAppRoot`）は Android 固有 API に密結合しているため
 * まだ共通化していない。ここでは Repository / Settings の iOS 実装が実際に動くことを
 * 示すため、ホームフィードを ViewModel 越しに読み込んで一覧表示する。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: IosIllustiaViewModel) {
    val state by viewModel.uiState.collectAsState()
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Illustia",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.homeItems, key = { it.id }) { illust ->
                    IllustRow(
                        title = illust.title,
                        artist = illust.artistName,
                    )
                }
            }
        }
    }
}

@Composable
private fun IllustRow(
    title: String,
    artist: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Swift から呼ばれるエントリ。`MainViewControllerKt.MainViewController()` として公開される。
 */
fun MainViewController(): UIViewController {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val strings = ComposeStringProvider()
    scope.launch { strings.preload() }
    val deps = IosViewModelDeps(applicationScope = scope, strings = strings)
    val viewModel = IosIllustiaViewModel(deps)
    return ComposeUIViewController {
        DisposableEffect(viewModel) {
            onDispose {
                viewModel.dispose()
                scope.cancel()
            }
        }
        App(viewModel)
    }
}
