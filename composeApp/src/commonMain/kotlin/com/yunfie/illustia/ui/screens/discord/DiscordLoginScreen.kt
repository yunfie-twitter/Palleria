package com.yunfie.illustia.ui.screens.discord

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.discord_login_title
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.HeaderIcon
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * ログイン画面の枠。WebView 本体は [LocalWebLoginContent] を供給する側が描く。
 */
@Composable
fun DiscordLoginScreen(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val webContent = LocalWebLoginContent.current

    BackNavigationHandler(onBack = onBack)

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.discord_login_title),
                largeTitle = stringResource(Res.string.discord_login_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        val contentModifier =
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(MiuixTheme.colorScheme.surface)
        if (webContent != null) {
            webContent(contentModifier, scaffoldPadding)
        }
    }
}
