package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The three separate trust decisions, in the order the user makes them. */
internal enum class TrustStep {
    SOURCE,
    KEY,
    POLICY,
}

/**
 * Progress for the trust ceremony: the user must be able to tell that accepting the policy is the
 * last of three independent decisions, not the only one.
 */
@Composable
internal fun TrustStepIndicator(
    current: TrustStep,
    modifier: Modifier = Modifier,
) {
    val steps = TrustStep.entries
    Row(
        modifier = modifier.fillMaxWidth().testTag("pallasync-trust-steps"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            val done = index < current.ordinal
            val active = step == current
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
            Box(Modifier.size(24.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
                if (done) {
                    Icon(MiuixIcons.Ok, contentDescription = null, tint = foreground, modifier = Modifier.size(14.dp))
                } else {
                    Text("${index + 1}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = foreground)
                }
            }
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

/**
 * Splits a fingerprint into display lines of 4-character groups.
 *
 * Presentation only: removing the spaces must yield the original string exactly, which is why the
 * grouping lives in a pure function that can be tested without rendering.
 */
internal fun fingerprintLines(value: String): List<String> = value.chunked(4).chunked(8).map { line -> line.joinToString(" ") }

/**
 * A key fingerprint, grouped so it can be read out loud and compared against another device.
 *
 * A single unbroken base64 run is effectively unverifiable by eye, which defeats the purpose of
 * asking the user to compare it. Grouping preserves the exact characters; only spacing changes.
 */
@Composable
internal fun TrustFingerprint(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().testTag("pallasync-trust-fingerprint"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
        // Split into 4-character groups; the original value is used verbatim, never re-encoded.
        fingerprintLines(value).forEach { line ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh, ROUNDED_FINGERPRINT)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A short explanatory line kept visually distinct from body text, so limits are not skimmed over. */
@Composable
internal fun TrustNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .background(MiuixTheme.colorScheme.primary, CircleShape),
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        )
    }
}

private val ROUNDED_FINGERPRINT = RoundedCornerShape(10.dp)
