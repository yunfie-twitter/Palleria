package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.StoredAccount
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

@Composable
fun AccountSwitchSheet(
    show: Boolean,
    accounts: List<StoredAccount>,
    activeAccountIndex: Int,
    viewModel: IllustiaViewModel,
    onDismiss: () -> Unit,
    onAddAccount: () -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<Int?>(null) }
    if (!show) return
    OverlayBottomSheet(
        show = true,
        title = stringResource(R.string.account_switch_title),
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        endAction = {
            IconButton(onClick = onAddAccount) {
                Icon(imageVector = MiuixIcons.Add, contentDescription = stringResource(R.string.action_add))
            }
        },
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                itemsIndexed(accounts, key = { index, account -> account.refreshToken }) { index, account ->
                    AccountRow(
                        account = account,
                        isActive = index == activeAccountIndex,
                        onSelect = { viewModel.switchAccount(index) },
                        onRemove = { pendingRemoval = index },
                    )
                }
                item {
                    AddAccountRow(onClick = onAddAccount)
                }
        }
    }

    pendingRemoval?.let { index ->
        val account = accounts.getOrNull(index)
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.account_remove_title),
            summary = stringResource(R.string.account_remove_summary, account?.name?.ifBlank { account.account } ?: ""),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.removeAccount(index)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun AccountRow(
    account: StoredAccount,
    isActive: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .squircleSurface(
                color = if (isActive) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 16.dp,
            )
            .then(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            val avatarUrl = account.profileImageUrl
            if (!avatarUrl.isNullOrBlank()) {
                PixivImage(
                    url = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    thumbnail = true,
                )
            } else {
                Icon(
                    imageVector = MiuixIcons.Contacts,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name.ifBlank { "@${account.account}" },
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "@${account.account}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isActive) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = stringResource(R.string.active),
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AddAccountRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 16.dp)
            .miuixClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = MiuixIcons.Add,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = stringResource(R.string.account_switch_add),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold,
        )
    }
}
