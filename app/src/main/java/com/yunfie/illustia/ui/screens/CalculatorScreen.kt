package com.yunfie.illustia.ui.screens
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.CalculatorHistoryEntry
import com.yunfie.illustia.IllustiaViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CalculatorScreen(
    buffer: String,
    history: List<CalculatorHistoryEntry>,
    isTransitioning: Boolean,
    viewModel: IllustiaViewModel,
) {
    // ロック中はバックナビゲーションを無効にする
    BackHandler(enabled = true) {}

    val buttonsEnabled = !isTransitioning

    // 遷移アニメーション完了後に confirmPrivacyUnlock を呼ぶ (Req 4.2, 4.9)
    LaunchedEffect(isTransitioning) {
        if (isTransitioning) {
            delay(280L) // AnimatedVisibility の exit アニメーション(250ms)完了を待つ
            viewModel.confirmPrivacyUnlock()
        }
    }

    // パターンC: 右上隅タップカウンター状態 (Req 2.6)
    var cornerTapCount by remember { mutableIntStateOf(0) }
    var cornerTapWindowStart by remember { mutableLongStateOf(0L) }
    var showCornerUnlockDialog by remember { mutableStateOf(false) }
    var cornerUnlockCode by remember { mutableStateOf("") }

    // 解除時フェードアウト + 上方向スライドアウト (Req 4.2)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { }
            },
    ) {
        AnimatedVisibility(
            visible = !isTransitioning,
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)) +
                   slideOutVertically(animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)) { -it / 8 },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // ── 履歴リスト（上部）──────────────────────────────────────────────
            CalculatorHistorySection(
                history = history,
                onVerifyAndUnlock = { code -> viewModel.verifyAndUnlockPrivacy(code) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            // ── 表示エリア ────────────────────────────────────────────────────
            CalculatorDisplay(
                buffer = buffer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            // ── ボタングリッド ────────────────────────────────────────────────
            CalculatorButtonGrid(
                enabled = buttonsEnabled,
                onAppend = { char -> viewModel.appendToCalculatorBuffer(char) },
                onClear = { viewModel.clearCalculatorBuffer() },
                onDelete = { viewModel.deleteLastCalculatorBuffer() },
                onEvaluate = { viewModel.evaluateCalculatorExpression() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            )
        }

        // ── パターンC: 右上隅タップエリア（72dp×72dp, 不可視）(Req 2.6) ──────
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(72.dp)
                .pointerInput(buttonsEnabled) {
                    detectTapGestures(
                        onTap = {
                            if (!buttonsEnabled) return@detectTapGestures
                            val now = System.currentTimeMillis()
                            if (cornerTapCount == 0 || now - cornerTapWindowStart > 2000L) {
                                // ウィンドウをリセットして新しいシーケンスを開始
                                cornerTapCount = 1
                                cornerTapWindowStart = now
                            } else {
                                cornerTapCount++
                            }
                            if (cornerTapCount >= 5) {
                                cornerTapCount = 0
                                cornerUnlockCode = ""
                                showCornerUnlockDialog = true
                            }
                        },
                    )
                },
        )

        // ── パターンC: 解除コード入力ダイアログ (Req 2.6, 4.6, 4.7) ─────────
        if (showCornerUnlockDialog) {
            OverlayDialog(
                show = true,
                title = "解除コードを入力",
                onDismissRequest = {
                    // キャンセル時: ダイアログを閉じるだけでフィードバックなし (Req 4.7)
                    showCornerUnlockDialog = false
                    cornerUnlockCode = ""
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextField(
                        value = cornerUnlockCode,
                        onValueChange = { cornerUnlockCode = it },
                        label = "解除コード",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = {
                                // キャンセル: フィードバックなしで閉じる (Req 4.7)
                                showCornerUnlockDialog = false
                                cornerUnlockCode = ""
                            },
                        ) {
                            Text("キャンセル")
                        }
                        Button(
                            onClick = {
                                val code = cornerUnlockCode
                                showCornerUnlockDialog = false
                                cornerUnlockCode = ""
                                // 照合成功: ViewModel が遷移を開始する (Req 4.6)
                                // 照合失敗: ダイアログを閉じるだけ、フィードバックなし (Req 4.7)
                                viewModel.verifyAndUnlockPrivacy(code)
                            },
                        ) {
                            Text("確認", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    }
    } // AnimatedVisibility
}

// ── 履歴セクション ─────────────────────────────────────────────────────────────

@Composable
private fun CalculatorHistorySection(
    history: List<CalculatorHistoryEntry>,
    onVerifyAndUnlock: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var showUnlockDialog by remember { mutableStateOf(false) }
    var unlockCode by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        // ヘッダー — 500ms 以上の長押しでパターンC解除ダイアログを表示する (Req 3.7)
        Text(
            text = "履歴",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 4.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            unlockCode = ""
                            showUnlockDialog = true
                        },
                    )
                },
        )

        // 最大 20 件のみ表示する (Req 2.5)
        val displayHistory = history.take(20)

        if (displayHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "計算履歴はありません",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = false,
            ) {
                items(displayHistory) { entry ->
                    CalculatorHistoryItem(entry = entry)
                }
            }
        }
    }

    // パターンC 解除コード入力ダイアログ (Req 3.7, 4.6, 4.7)
    if (showUnlockDialog) {
        OverlayDialog(
            show = true,
            title = "解除コードを入力",
            onDismissRequest = {
                // キャンセル時: ダイアログを閉じるだけでフィードバックなし (Req 4.7)
                showUnlockDialog = false
                unlockCode = ""
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextField(
                    value = unlockCode,
                    onValueChange = { unlockCode = it },
                    label = "解除コード",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            // キャンセル: フィードバックなしで閉じる (Req 4.7)
                            showUnlockDialog = false
                            unlockCode = ""
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("キャンセル")
                    }
                    Button(
                        onClick = {
                            val code = unlockCode
                            showUnlockDialog = false
                            unlockCode = ""
                            // 照合成功: ViewModel が遷移を開始する (Req 4.6)
                            // 照合失敗: ダイアログを閉じるだけ、フィードバックなし (Req 4.7)
                            onVerifyAndUnlock(code)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("確認", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalculatorHistoryItem(entry: CalculatorHistoryEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.expression,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "= ${entry.result}",
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

// ── 表示エリア ─────────────────────────────────────────────────────────────────

@Composable
private fun CalculatorDisplay(
    buffer: String,
    modifier: Modifier = Modifier,
) {
    val displayText = if (buffer.isEmpty()) "0" else buffer

    Text(
        text = displayText,
        color = MiuixTheme.colorScheme.onSurface,
        style = MiuixTheme.textStyles.headline1,
        fontSize = 48.sp,
        fontWeight = FontWeight.Light,
        textAlign = TextAlign.End,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

// ── ボタングリッド ─────────────────────────────────────────────────────────────

@Composable
private fun CalculatorButtonGrid(
    enabled: Boolean,
    onAppend: (Char) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onEvaluate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 4 列×5 行の均等グリッド。
    // 各マスは weight(1f) / aspectRatio(1f) で正方形にそろえ、
    // 0 は 2 マス分横に伸ばして aspectRatio(2f) で高さを他のマスと揃える。
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Row 1: C, ⌫, ÷, ×
        CalcRow {
            SpecialButton(label = "C", enabled = enabled, modifier = Modifier.weight(1f)) { onClear() }
            SpecialButton(label = "⌫", enabled = enabled, modifier = Modifier.weight(1f)) { onDelete() }
            OperatorButton(label = "÷", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('÷') }
            OperatorButton(label = "×", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('×') }
        }
        // Row 2: 7, 8, 9, -
        CalcRow {
            DigitButton(label = "7", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('7') }
            DigitButton(label = "8", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('8') }
            DigitButton(label = "9", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('9') }
            OperatorButton(label = "-", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('-') }
        }
        // Row 3: 4, 5, 6, +
        CalcRow {
            DigitButton(label = "4", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('4') }
            DigitButton(label = "5", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('5') }
            DigitButton(label = "6", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('6') }
            OperatorButton(label = "+", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('+') }
        }
        // Row 4: 1, 2, 3, blank (= の上は空欄)
        CalcRow {
            DigitButton(label = "1", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('1') }
            DigitButton(label = "2", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('2') }
            DigitButton(label = "3", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('3') }
            Spacer(modifier = Modifier.weight(1f))
        }
        // Row 5: ., 0 (span 2), =
        CalcRow {
            DigitButton(label = ".", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('.') }
            WideDigitButton(label = "0", enabled = enabled, modifier = Modifier.weight(2f)) { onAppend('0') }
            EqualsButton(enabled = enabled, modifier = Modifier.weight(1f)) { onEvaluate() }
        }
    }
}

// ── ヘルパー Composable ────────────────────────────────────────────────────────

@Composable
private fun CalcRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun DigitButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                if (enabled) MiuixTheme.colorScheme.surfaceContainer
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WideDigitButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // 0 は 2 マス横に伸ばし、高さは 1 マス分に抑える（aspectRatio(2f)）
    Box(
        modifier = modifier
            .aspectRatio(2f)
            .clip(CircleShape)
            .background(
                if (enabled) MiuixTheme.colorScheme.surfaceContainer
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun OperatorButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val operatorTextColor = if (enabled) Color(0xFFFD8D35) else Color(0x99FD8D35)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                if (enabled) MiuixTheme.colorScheme.surfaceContainer
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = operatorTextColor,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EqualsButton(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val xiaomiColor = Color(0xFFFD8D35)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                if (enabled) xiaomiColor else xiaomiColor.copy(alpha = 0.4f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "=",
            color = Color.White,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SpecialButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(
                if (enabled) MiuixTheme.colorScheme.surfaceContainerHighest
                else MiuixTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Medium,
        )
    }
}
