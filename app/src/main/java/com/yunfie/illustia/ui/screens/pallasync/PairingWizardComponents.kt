package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Pairing progress. The flow has three observable milestones, and showing them up front stops the
 * user from wondering whether the QR code is the last step or whether a confirmation is still owed.
 */
internal enum class PairingStep {
    SHOW_CODE,
    SCAN,
    CONFIRM,
}

/** Horizontal stepper: completed steps stay visible so the user can see how far they have come. */
@Composable
internal fun PairingStepper(
    current: PairingStep,
    modifier: Modifier = Modifier,
) {
    val steps = PairingStep.entries
    Row(
        modifier = modifier.fillMaxWidth().testTag("pallasync-pairing-stepper"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            val done = index < current.ordinal
            val active = step == current
            StepDot(number = index + 1, done = done, active = active)
            if (index < steps.lastIndex) {
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .height(2.dp)
                        .background(
                            if (done) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.dividerLine,
                        ),
                )
            }
        }
    }
}

@Composable
private fun StepDot(
    number: Int,
    done: Boolean,
    active: Boolean,
) {
    val background =
        when {
            done || active -> MiuixTheme.colorScheme.primary
            else -> MiuixTheme.colorScheme.surfaceContainerHigh
        }
    val foreground =
        when {
            done || active -> MiuixTheme.colorScheme.onPrimaryContainer
            else -> MiuixTheme.colorScheme.onSurfaceContainerVariant
        }
    Box(
        modifier = Modifier.size(26.dp).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Text(text = number.toString(), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = foreground)
        }
    }
}

/** Role choice: the two sides of pairing need different instructions, so they are separated. */
@Composable
internal fun PairingRoleCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        if (selected) MiuixTheme.colorScheme.primaryContainer else MiuixTheme.colorScheme.surfaceContainerHigh
    val accent = if (selected) MiuixTheme.colorScheme.onPrimaryContainer else MiuixTheme.colorScheme.onSurfaceContainer
    top.yukonga.miuix.kmp.basic.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = ROUNDED_CARD,
        color = container,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = accent)
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color =
                        if (selected) {
                            MiuixTheme.colorScheme.onPrimaryContainer
                        } else {
                            MiuixTheme.colorScheme.onSurfaceContainerVariant
                        },
                )
            }
        }
    }
}

/**
 * SAS digits are the only defence against a swapped peer, so they are rendered large and
 * monospaced in groups: a digit-by-digit comparison must not be defeated by spacing alone.
 */
@Composable
internal fun PairingSasCode(
    sas: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag("pallasync-pairing-sas"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        sas.chunked(4).forEach { group ->
            Box(
                modifier =
                    Modifier
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh, ROUNDED_CARD)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = group,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Step caption: states the single action the current step expects. */
@Composable
internal fun PairingStepCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(4.dp))
    }
}

private val ROUNDED_CARD = RoundedCornerShape(16.dp)
