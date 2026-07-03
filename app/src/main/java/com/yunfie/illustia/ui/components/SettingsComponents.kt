package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Switch
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SmallTitle(text = title)
        content()
    }
}

@Composable
fun SettingRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    action: @Composable () -> Unit
) {
    BasicComponent(
        title = title,
        summary = summary,
        modifier = Modifier.fillMaxWidth(),
        startAction = icon?.let {
            {
                PreferenceIcon(it)
            }
        },
        endActions = {
            action()
        },
    )
}

@Composable
fun SettingLinkRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    BasicComponent(
        title = title,
        summary = summary,
        modifier = Modifier.fillMaxWidth(),
        startAction = icon?.let {
            {
                PreferenceIcon(it)
            }
        },
        endActions = {
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
        role = Role.Button,
    )
}

@Composable
private fun PreferenceIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier
            .size(28.dp)
            .padding(end = 12.dp)
    )
}

@Composable
fun ElevatedPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun HeroPanel(title: String, body: String) {
    Card(
        modifier = Modifier
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .fillMaxWidth(),
        cornerRadius = 24.dp,
        insideMargin = PaddingValues(20.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.primaryContainer,
            contentColor = MiuixTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(text = title, fontWeight = FontWeight.Black, color = MiuixTheme.colorScheme.onPrimaryContainer)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = body, color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f))
    }
}

@Composable
fun MiuixConfirmDialog(
    show: Boolean,
    title: String,
    summary: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    OverlayDialog(
        show = show,
        title = title,
        summary = summary,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = overlayActionButtonColors(),
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = overlayActionButtonColors(),
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            ) {
                Text(confirmText, color = if (destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun <T> SettingDropdownRow(
    title: String,
    selected: T,
    values: List<T>,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
) {
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    OverlayDropdownPreference(
        title = title,
        summary = summary,
        items = values.map { label(it) },
        selectedIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth(),
        startAction = icon?.let {
            {
                PreferenceIcon(it)
            }
        },
        onSelectedIndexChange = { index ->
            values.getOrNull(index)?.let(onSelect)
        },
    )
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    BasicComponent(
        title = title,
        summary = summary,
        modifier = Modifier.fillMaxWidth(),
        startAction = icon?.let {
            {
                PreferenceIcon(it)
            }
        },
        endActions = {
            Switch(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                enabled = enabled,
            )
        },
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else {
            null
        },
        role = Role.Switch,
        enabled = enabled,
    )
}

@Composable
fun <T> ChoiceRow(
    values: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { value ->
                    val isSelected = value == selected
                    Button(
                        onClick = { onSelect(value) },
                        modifier = Modifier.weight(1f),
                        colors = if (isSelected) {
                            ButtonDefaults.buttonColorsPrimary()
                        } else {
                            ButtonDefaults.buttonColors(
                                color = MiuixTheme.colorScheme.surfaceContainer,
                            )
                        },
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text(
                            label(value),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isSelected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun <T> FlowButtons(values: List<T>, label: (T) -> String, onClick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { value ->
                    Button(
                        onClick = { onClick(value) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                            contentColor = MiuixTheme.colorScheme.onSurface,
                        ),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        Text(label(value), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SmallTitle(
            text = title,
            modifier = Modifier.weight(1f),
            insideMargin = PaddingValues(0.dp),
        )
        if (action != null && onAction != null) {
            TextButton(
                text = action,
                onClick = onAction,
                minHeight = 32.dp,
                insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
