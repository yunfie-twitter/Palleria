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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.CalculatorHistoryEntry
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CalculatorScreen(
    viewModel: IllustiaViewModel,
    isTransitioning: Boolean = false,
    buffer: String? = null,
    history: List<CalculatorHistoryEntry>? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val calcState by viewModel.calculatorState.collectAsStateWithLifecycle()
    val activeBuffer = buffer ?: calcState.buffer
    val activeHistory = history ?: calcState.history
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
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { }
                },
    ) {
        AnimatedVisibility(
            visible = !isTransitioning,
            enter = EnterTransition.None,
            exit =
                fadeOut(animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)) +
                    slideOutVertically(animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing)) { -it / 8 },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface),
            ) {
                val configuration = LocalConfiguration.current
                val useWideLayout =
                    configuration.screenWidthDp >= 600 &&
                        configuration.screenWidthDp > configuration.screenHeightDp
                if (useWideLayout) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            CalculatorHistorySection(
                                history = activeHistory,
                                onVerifyAndUnlock = { code ->
                                    coroutineScope.launch {
                                        viewModel.verifyAndUnlockPrivacy(code)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                            CalculatorDisplay(
                                buffer = activeBuffer,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        }
                        CalculatorButtonGrid(
                            enabled = buttonsEnabled,
                            onAppend = { char -> viewModel.appendToCalculatorBuffer(char) },
                            onClear = viewModel::clearCalculatorBuffer,
                            onDelete = viewModel::deleteLastCalculatorBuffer,
                            onEvaluate = viewModel::evaluateCalculatorExpression,
                            compact = true,
                            modifier = Modifier.weight(1f).widthIn(max = 480.dp),
                        )
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // ── 履歴リスト（上部）──────────────────────────────────────────────
                        CalculatorHistorySection(
                            history = activeHistory,
                            onVerifyAndUnlock = { code ->
                                coroutineScope.launch {
                                    viewModel.verifyAndUnlockPrivacy(code)
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                        )

                        // ── 表示エリア ────────────────────────────────────────────────────
                        CalculatorDisplay(
                            buffer = activeBuffer,
                            modifier =
                                Modifier
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
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                        )
                    }
                }

                // ── パターンC: 右上隅タップエリア（72dp×72dp, 不可視）(Req 2.6) ──────
                Box(
                    modifier =
                        Modifier
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
                        title = stringResource(R.string.privacy_enter_unlock_code),
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
                                label = stringResource(R.string.privacy_unlock_code),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions =
                                    KeyboardOptions(
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
                                    colors = overlayActionButtonColors(),
                                ) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                                Button(
                                    onClick = {
                                        val code = cornerUnlockCode
                                        showCornerUnlockDialog = false
                                        cornerUnlockCode = ""
                                        // 照合成功: ViewModel が遷移を開始する (Req 4.6)
                                        // 照合失敗: ダイアログを閉じるだけ、フィードバックなし (Req 4.7)
                                        coroutineScope.launch {
                                            viewModel.verifyAndUnlockPrivacy(code)
                                        }
                                    },
                                    colors = overlayActionButtonColors(),
                                ) {
                                    Text(stringResource(R.string.action_confirm), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    } // AnimatedVisibility
}
