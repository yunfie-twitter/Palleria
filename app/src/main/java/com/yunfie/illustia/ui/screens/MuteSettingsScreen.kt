package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun MuteSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    var showAddTagSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.mute_settings_title),
                largeTitle = stringResource(R.string.mute_settings_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 18.dp,
                end = 18.dp,
                top = scaffoldPadding.calculateTopPadding() + 14.dp,
                bottom = 96.dp,
            ),
        ) {
            item {
                Section(stringResource(R.string.mute_section_users)) {
                    if (state.settings.mutedUsers.isEmpty()) {
                        Text(
                            stringResource(R.string.mute_users_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            items(state.settings.mutedUsers, key = { "user_$it" }) { userId ->
                MuteItemRow(text = "User ID: $userId") {
                    viewModel.unmuteUser(userId)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            item {
                Section(stringResource(R.string.mute_section_works)) {
                    if (state.settings.mutedIllusts.isEmpty()) {
                        Text(
                            stringResource(R.string.mute_works_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            items(state.settings.mutedIllusts, key = { "illust_$it" }) { illustId ->
                MuteItemRow(text = "Illust ID: $illustId") {
                    viewModel.unmuteIllust(illustId)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SmallTitle(text = stringResource(R.string.mute_section_tags))
                        IconButton(onClick = { showAddTagSheet = true }) {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = stringResource(R.string.action_add),
                            )
                        }
                    }
                    if (state.settings.mutedTags.isEmpty()) {
                        Text(
                            stringResource(R.string.mute_tags_empty),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            items(state.settings.mutedTags, key = { "tag_$it" }) { tag ->
                MuteItemRow(text = "#$tag") {
                    viewModel.unmuteTag(tag)
                }
            }
        }
    }

    AddMuteTagSheet(
        show = showAddTagSheet,
        onDismiss = { showAddTagSheet = false },
        onAdd = { tag ->
            viewModel.muteTag(tag)
            showAddTagSheet = false
        },
    )
}

@Composable
fun MuteItemRow(text: String, onDelete: () -> Unit) {
    BasicComponent(
        title = text,
        modifier = Modifier.fillMaxWidth(),
        endActions = {
            HeaderIcon(MiuixIcons.Delete, onClick = onDelete)
        },
    )
}

@Composable
private fun AddMuteTagSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    if (!show) return

    var tagInput by remember { mutableStateOf("") }

    OverlayBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(R.string.mute_add_tag_title),
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        onDismissRequest = onDismiss,
        insideMargin = BottomSheetInsideMargin,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TextField(
                value = tagInput,
                onValueChange = { tagInput = it },
                label = stringResource(R.string.mute_add_tag_label),
                useLabelAsPlaceholder = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val normalized = tagInput.trim().removePrefix("#").trim()
                    if (normalized.isNotEmpty()) {
                        onAdd(normalized)
                        tagInput = ""
                    }
                },
                enabled = tagInput.trim().removePrefix("#").trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = overlayActionButtonColors(),
            ) {
                Text(stringResource(R.string.action_add), fontWeight = FontWeight.Bold)
            }
        }
    }
}
