package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.performAppHapticFeedback
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PinSetupScreen(
    isChange: Boolean,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current

    var step by remember { mutableIntStateOf(0) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }

    fun reset() {
        step = 0
        newPin = ""
        confirmPin = ""
        pin = ""
        error = ""
    }

    LaunchedEffect(Unit) {
        step = if (isChange) 1 else 2
    }

    LaunchedEffect(shake) {
        if (shake) {
            kotlinx.coroutines.delay(400)
            shake = false
        }
    }

    val title = when (step) {
        1 -> stringResource(R.string.app_lock_enter_current)
        2 -> stringResource(R.string.app_lock_enter_new)
        3 -> stringResource(R.string.app_lock_confirm_new)
        else -> ""
    }

    val mismatchError = stringResource(R.string.app_lock_mismatch)
    val incorrectError = stringResource(R.string.app_lock_incorrect)

    fun onDigitPressed(digit: Char) {
        performAppHapticFeedback(context, haptic, hapticMode)
        if (error.isNotBlank()) {
            error = ""
            when (step) {
                1 -> pin = digit.toString()
                2 -> newPin = digit.toString()
                3 -> confirmPin = digit.toString()
            }
            return
        }
        when (step) {
            1 -> {
                if (pin.length >= 6) return
                pin += digit
                if (pin.length == 6) {
                    if (viewModel.verifyPin(pin)) {
                        pin = ""
                        step = 2
                    } else {
                        error = incorrectError
                        shake = true
                        pin = ""
                    }
                }
            }
            2 -> {
                if (newPin.length >= 6) return
                newPin += digit
                if (newPin.length == 6) {
                    step = 3
                }
            }
            3 -> {
                if (confirmPin.length >= 6) return
                confirmPin += digit
                if (confirmPin.length == 6) {
                    if (confirmPin == newPin) {
                        if (isChange) {
                            viewModel.changePin(newPin)
                        } else {
                            viewModel.setupPin(newPin)
                        }
                        reset()
                        onBack()
                    } else {
                        error = mismatchError
                        shake = true
                        confirmPin = ""
                    }
                }
            }
        }
    }

    fun onDeletePressed() {
        performAppHapticFeedback(context, haptic, hapticMode)
        if (error.isNotBlank()) {
            error = ""
            return
        }
        when (step) {
            1 -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
            2 -> if (newPin.isNotEmpty()) newPin = newPin.dropLast(1)
            3 -> if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
        }
    }

    val displayPin = when (step) {
        1 -> pin
        2 -> newPin
        3 -> confirmPin
        else -> ""
    }
    val translateX = if (shake) 12.dp else 0.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = if (shake) translateX else 0.dp),
            ) {
                repeat(6) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    error.isNotBlank() -> MiuixTheme.colorScheme.error
                                    index < displayPin.length -> MiuixTheme.colorScheme.primary
                                    else -> MiuixTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            ),
                    )
                }
            }

            if (error.isNotBlank()) {
                Text(
                    text = error,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1,
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            NumberPad(
                onDigit = ::onDigitPressed,
                onDelete = ::onDeletePressed,
            )
        }
    }
}

@Composable
private fun NumberPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    PadButton(label = digit.toString(), onClick = { onDigit(digit) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(modifier = Modifier.size(72.dp))
            PadButton(label = "0", onClick = { onDigit('0') })
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Remove,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(28.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun PadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Medium,
        )
    }
}
