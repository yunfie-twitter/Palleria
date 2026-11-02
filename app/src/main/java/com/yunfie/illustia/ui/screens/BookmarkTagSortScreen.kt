package com.yunfie.illustia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.pixiv.BookmarkTag
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BookmarkTagSortScreen(
    settings: AppSettings,
    repository: IllustiaRepository,
    selectedTag: String?,
    onBack: () -> Unit,
    onSelect: (Restrict, String?) -> Unit,
) {
    BackHandler(onBack = onBack)
    var restrict by remember(settings.bookmarkRestrict) { mutableStateOf(settings.bookmarkRestrict) }
    var tags by remember(restrict) { mutableStateOf<List<BookmarkTag>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember(restrict) { mutableStateOf(false) }
    var error by remember(restrict) { mutableStateOf(false) }
    var retryCount by remember(restrict) { mutableStateOf(0) }
    val userId = settings.bookmarkUserId

    LaunchedEffect(restrict, userId, retryCount) {
        if (userId == null) {
            tags = emptyList()
            error = true
            return@LaunchedEffect
        }
        loading = true
        error = false
        runCatching { repository.illustBookmarkTags(userId, restrict) }
            .onSuccess { tags = it.sortedBy { tag -> tag.name.lowercase() } }
            .onFailure { error = true }
        loading = false
    }

    val filteredTags = remember(tags, query) { tags.filter { it.name.contains(query.trim(), ignoreCase = true) } }
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.bookmark_tag_sort_title),
                largeTitle = stringResource(R.string.bookmark_tag_sort_title),
                navigationIcon = {
                    HeaderIcon(
                        icon = MiuixIcons.Back,
                        onClick = onBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BookmarkTagRestrictTab(
                    text = stringResource(R.string.restrict_public),
                    selected = restrict == Restrict.Public,
                    modifier = Modifier.weight(1f),
                    onClick = { restrict = Restrict.Public },
                )
                BookmarkTagRestrictTab(
                    text = stringResource(R.string.restrict_private),
                    selected = restrict == Restrict.Private,
                    modifier = Modifier.weight(1f),
                    onClick = { restrict = Restrict.Private },
                )
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.bookmark_tag_sort_search),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when {
                loading -> {
                    LoadingIndicator(modifier = Modifier.padding(20.dp).align(Alignment.CenterHorizontally))
                }

                error -> {
                    Text(
                        text = stringResource(R.string.bookmark_tag_sort_error),
                        color = MiuixTheme.colorScheme.error,
                        modifier = Modifier.padding(18.dp),
                    )
                    Button(
                        onClick = {
                            retryCount++
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text(stringResource(R.string.dialog_reload)) }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        item(key = "all") {
                            BookmarkTagSortRow(
                                name = stringResource(R.string.bookmark_tag_sort_all),
                                count = null,
                                selected = selectedTag == null,
                                onClick = { onSelect(restrict, null) },
                            )
                        }
                        item(key = "unclassified") {
                            BookmarkTagSortRow(
                                name = stringResource(R.string.bookmark_tag_unclassified),
                                count = null,
                                selected = selectedTag == "",
                                onClick = { onSelect(restrict, "") },
                            )
                        }
                        items(filteredTags, key = { it.name }) { tag ->
                            BookmarkTagSortRow(
                                name = tag.name,
                                count = tag.count,
                                selected = selectedTag == tag.name,
                                onClick = { onSelect(restrict, tag.name) },
                            )
                        }
                        if (filteredTags.isEmpty() && !loading) {
                            item {
                                Text(
                                    text = stringResource(R.string.bookmark_tag_sort_empty),
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarkTagRestrictTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth(0.34f).height(3.dp).background(if (selected) MiuixTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
private fun BookmarkTagSortRow(
    name: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        )
        if (count != null) {
            Text(text = count.toString(), color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }
        if (selected) {
            Text(text = "✓", modifier = Modifier.padding(start = 12.dp), color = MiuixTheme.colorScheme.primary)
        }
    }
}
