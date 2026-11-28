package com.yunfie.illustia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * iOS アプリのルート画面。
 *
 * 現段階では Repository / Settings の iOS 実装が無いため、起動確認用の最小画面を出す。
 * プラットフォーム実装が揃ったら本編のナビゲーションに差し替える。
 */
@Composable
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Illustia", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

/**
 * Swift から呼ばれるエントリ。`MainViewControllerKt.MainViewController()` として公開される。
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
