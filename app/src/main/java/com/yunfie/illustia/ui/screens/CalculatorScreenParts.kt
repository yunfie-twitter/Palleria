package com.yunfie.illustia.ui.screens

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.CalculatorHistoryEntry
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LocalCompactCalculatorButtons = compositionLocalOf { false }

@Composable
internal fun CalculatorHistorySection(
    history: List<CalculatorHistoryEntry>,
    onVerifyAndUnlock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var showUnlockDialog by remember { mutableStateOf(false) }
    var unlockCode by remember { mutableStateOf("") }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.calculator_history),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            modifier =
                Modifier
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

        val displayHistory = history.take(20)

        if (displayHistory.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.calculator_history_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                reverseLayout = false,
            ) {
                items(
                    items = displayHistory,
                    key = { it.id },
                ) { entry ->
                    CalculatorHistoryItem(entry = entry)
                }
            }
        }
    }

    if (showUnlockDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.privacy_enter_unlock_code),
            onDismissRequest = {
                showUnlockDialog = false
                unlockCode = ""
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextField(
                    value = unlockCode,
                    onValueChange = { unlockCode = it },
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
                            showUnlockDialog = false
                            unlockCode = ""
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            val code = unlockCode
                            showUnlockDialog = false
                            unlockCode = ""
                            onVerifyAndUnlock(code)
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(stringResource(R.string.action_confirm), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CalculatorHistoryItem(entry: CalculatorHistoryEntry) {
    Row(
        modifier =
            Modifier
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

@Composable
internal fun CalculatorDisplay(
    buffer: String,
    modifier: Modifier = Modifier,
) {
    val displayText =
        when {
            buffer.isEmpty() -> "0"
            buffer == "エラー" -> stringResource(R.string.calculator_error)
            else -> buffer
        }

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

@Composable
internal fun CalculatorButtonGrid(
    enabled: Boolean,
    onAppend: (Char) -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onEvaluate: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    CompositionLocalProvider(LocalCompactCalculatorButtons provides compact) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            CalcRow {
                SpecialButton(label = "C", enabled = enabled, modifier = Modifier.weight(1f)) { onClear() }
                SpecialButton(label = "⌫", enabled = enabled, modifier = Modifier.weight(1f)) { onDelete() }
                OperatorButton(label = "÷", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('÷') }
                OperatorButton(label = "×", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('×') }
            }
            CalcRow {
                DigitButton(label = "7", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('7') }
                DigitButton(label = "8", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('8') }
                DigitButton(label = "9", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('9') }
                OperatorButton(label = "-", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('-') }
            }
            CalcRow {
                DigitButton(label = "4", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('4') }
                DigitButton(label = "5", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('5') }
                DigitButton(label = "6", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('6') }
                OperatorButton(label = "+", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('+') }
            }
            CalcRow {
                DigitButton(label = "1", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('1') }
                DigitButton(label = "2", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('2') }
                DigitButton(label = "3", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('3') }
                Spacer(modifier = Modifier.weight(1f))
            }
            CalcRow {
                DigitButton(label = ".", enabled = enabled, modifier = Modifier.weight(1f)) { onAppend('.') }
                WideDigitButton(label = "0", enabled = enabled, modifier = Modifier.weight(2f)) { onAppend('0') }
                EqualsButton(enabled = enabled, modifier = Modifier.weight(1f)) { onEvaluate() }
            }
        }
    }
}

@Composable
internal fun CalcRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (LocalCompactCalculatorButtons.current) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
internal fun DigitButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(if (LocalCompactCalculatorButtons.current) 1.75f else 1f)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        MiuixTheme.colorScheme.surfaceContainer
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
                    },
                ).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color =
                if (enabled) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                },
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun WideDigitButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(if (LocalCompactCalculatorButtons.current) 3.65f else 2f)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        MiuixTheme.colorScheme.surfaceContainer
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
                    },
                ).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color =
                if (enabled) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                },
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun OperatorButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val operatorTextColor = if (enabled) Color(0xFFFD8D35) else Color(0x99FD8D35)

    Box(
        modifier =
            modifier
                .aspectRatio(if (LocalCompactCalculatorButtons.current) 1.75f else 1f)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        MiuixTheme.colorScheme.surfaceContainer
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
                    },
                ).clickable(enabled = enabled) { onClick() },
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
internal fun EqualsButton(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val xiaomiColor = Color(0xFFFD8D35)
    Box(
        modifier =
            modifier
                .aspectRatio(if (LocalCompactCalculatorButtons.current) 1.75f else 1f)
                .clip(CircleShape)
                .background(
                    if (enabled) xiaomiColor else xiaomiColor.copy(alpha = 0.4f),
                ).clickable(enabled = enabled) { onClick() },
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
internal fun SpecialButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(if (LocalCompactCalculatorButtons.current) 1.75f else 1f)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        MiuixTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MiuixTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
                    },
                ).clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color =
                if (enabled) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                },
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Medium,
        )
    }
}
