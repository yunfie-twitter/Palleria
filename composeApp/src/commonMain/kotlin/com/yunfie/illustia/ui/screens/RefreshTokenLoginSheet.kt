package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_close
import com.yunfie.illustia.resources.login_refresh_token_hide
import com.yunfie.illustia.resources.login_refresh_token_label
import com.yunfie.illustia.resources.login_refresh_token_show
import com.yunfie.illustia.resources.login_token_button
import com.yunfie.illustia.resources.login_token_title
import com.yunfie.illustia.ui.components.AppBottomSheet
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.NonAmoledDarkTheme
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.screens.account.LoginActions
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
private fun sheetBottomInsets(): WindowInsets =
    WindowInsets.navigationBars
        .union(WindowInsets.ime)
        .only(WindowInsetsSides.Bottom)

@Composable
fun RefreshTokenLoginBottomSheet(
    state: IllustiaUiState,
    viewModel: LoginActions,
    onDismiss: () -> Unit,
) {
    var tokenVisible by remember { mutableStateOf(false) }
    AppBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(Res.string.login_token_title),
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
        // シート側の自動インセット処理（imePadding）を切り、内側で navigationBars と ime の
        // 「大きい方」だけを確保する。既定のままだとルートが消費済みのナビゲーションバー分と
        // ime 分が二重に積まれ、キーボードとの間に隙間ができる。
        defaultWindowInsetsPadding = false,
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(Res.string.action_close))
            }
        },
    ) {
        NonAmoledDarkTheme {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(sheetBottomInsets())
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TextField(
                    value = state.settings.refreshToken,
                    onValueChange = viewModel::updateRefreshToken,
                    label = stringResource(Res.string.login_refresh_token_label),
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = if (tokenVisible) MiuixIcons.Hide else MiuixIcons.Show,
                                contentDescription =
                                    stringResource(
                                        if (tokenVisible) Res.string.login_refresh_token_hide else Res.string.login_refresh_token_show,
                                    ),
                            )
                        }
                    },
                )
                Button(
                    onClick = viewModel::login,
                    enabled = state.settings.refreshToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(stringResource(Res.string.login_token_button))
                }
                StateBanner(state.loadState)
            }
        }
    }
}
