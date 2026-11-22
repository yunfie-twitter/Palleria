package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 画面全体のエラー表示を中央に置く。
 *
 * [RequestErrorState] は fillMaxWidth() だけで高さを持たないため、Lazy リストの
 * 先頭要素として積むと上端に張り付き、コンテンツが空のとき「上寄りに小さく出ている」
 * だけになる。一覧が丸ごと空のときはこちらを使う。
 */
@Composable
fun CenteredErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        RequestErrorState(message = message, onRetry = onRetry)
    }
}

/** [CenteredErrorState] の空状態版。 */
@Composable
fun CenteredEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(message)
    }
}
