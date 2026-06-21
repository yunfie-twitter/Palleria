package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AccountLoginMethodScreen(
    onBack: () -> Unit,
    onWebLogin: () -> Unit,
    onRefreshTokenLogin: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.account_add_title),
                largeTitle = stringResource(R.string.account_add_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = { HeaderIcon(MiuixIcons.Back, onClick = onBack) },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = scaffoldPadding.calculateTopPadding() + 10.dp,
                bottom = 48.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.account_add_description),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body1,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }
            item {
                LoginMethodCard(
                    icon = MiuixIcons.Contacts,
                    title = stringResource(R.string.account_web_login),
                    description = stringResource(R.string.account_add_web_description),
                    badge = stringResource(R.string.account_add_recommended),
                    emphasized = true,
                    onClick = onWebLogin,
                )
            }
            item {
                LoginMethodCard(
                    icon = MiuixIcons.Refresh,
                    title = stringResource(R.string.account_token_login),
                    description = stringResource(R.string.account_add_token_description),
                    onClick = onRefreshTokenLogin,
                )
            }
        }
    }
}

@Composable
private fun LoginMethodCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    badge: String? = null,
    emphasized: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .squircleSurface(
                color = if (emphasized) MiuixTheme.colorScheme.primary.copy(alpha = 0.09f)
                else MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 22.dp,
            )
            .miuixClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .squircleSurface(
                    color = if (emphasized) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.surfaceContainerHigh,
                    cornerRadius = 15.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasized) MiuixTheme.colorScheme.onPrimary
                else MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                )
                badge?.let {
                    Text(
                        text = it,
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .squircleSurface(
                                color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                cornerRadius = 8.dp,
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(
                text = description,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }

        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(20.dp),
        )
    }
}
