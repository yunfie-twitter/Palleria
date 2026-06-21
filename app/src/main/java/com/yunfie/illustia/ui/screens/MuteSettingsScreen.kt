package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MuteSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
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

        item { Section(stringResource(R.string.mute_section_users)) {
            if (state.settings.mutedUsers.isEmpty()) {
                Text(stringResource(R.string.mute_users_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }}
        items(state.settings.mutedUsers, key = { "user_$it" }) { userId ->
            MuteItemRow(text = "User ID: $userId") {
                viewModel.unmuteUser(userId)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }

        item { Section(stringResource(R.string.mute_section_works)) {
            if (state.settings.mutedIllusts.isEmpty()) {
                Text(stringResource(R.string.mute_works_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }}
        items(state.settings.mutedIllusts, key = { "illust_$it" }) { illustId ->
            MuteItemRow(text = "Illust ID: $illustId") {
                viewModel.unmuteIllust(illustId)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }

        item { Section(stringResource(R.string.mute_section_tags)) {
            if (state.settings.mutedTags.isEmpty()) {
                Text(stringResource(R.string.mute_tags_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }}
        items(state.settings.mutedTags, key = { "tag_$it" }) { tag ->
            MuteItemRow(text = "#$tag") {
                viewModel.unmuteTag(tag)
            }
        }
    }
    }
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
