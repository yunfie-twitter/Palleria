package com.yunfie.illustia.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.PixivWebLoginRequest
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.NonAmoledDarkTheme
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.StateBanner
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun RefreshTokenLoginBottomSheet(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onDismiss: () -> Unit,
) {
    OverlayBottomSheet(
        show = true,
        modifier = Modifier.scrollEndHaptic(),
        title = stringResource(R.string.login_token_title),
        onDismissRequest = onDismiss,
        backgroundColor = LocalBottomSheetBackgroundColor.current,
        insideMargin = BottomSheetInsideMargin,
        startAction = {
            IconButton(onClick = onDismiss) {
                Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
    ) {
        NonAmoledDarkTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TextField(
                    value = state.settings.refreshToken,
                    onValueChange = viewModel::updateRefreshToken,
                    label = "Pixiv refresh token",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::login,
                    enabled = state.settings.refreshToken.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(stringResource(R.string.login_token_button))
                }
                StateBanner(state.loadState)
            }
        }
    }
}

@Composable
fun PixivWebLoginScreen(
    request: PixivWebLoginRequest,
    onCodeReceived: (String) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
    onWebViewChanged: (WebView?) -> Unit = {},
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var completed by remember { mutableStateOf(false) }
    var webError by remember { mutableStateOf<String?>(null) }

    fun completeWithUrl(url: String): Boolean {
        val code = pixivLoginCodeOrNull(url) ?: return false
        if (!completed) {
            completed = true
            onCodeReceived(code)
        }
        return true
    }

    PredictiveBackGestureHandler {
        val activeWebView = webView
        if (activeWebView?.canGoBack() == true) {
            activeWebView.goBack()
        } else {
            onCancel()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onWebViewChanged(null)
            webView?.destroy()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.82f)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        onWebViewChanged(this)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.userAgentString = "PixivAndroidApp/6.184.0 (Android 14; Illustia)"
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                return completeWithUrl(request.url.toString())
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                if (completeWithUrl(url)) {
                                    view.stopLoading()
                                    return
                                }
                                webError = null
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                isLoading = false
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError,
                            ) {
                                if (request.isForMainFrame && completeWithUrl(request.url.toString())) {
                                    view.stopLoading()
                                    return
                                }
                                if (request.isForMainFrame && !completed) {
                                    isLoading = false
                                    webError = error.description?.toString()?.ifBlank { context.getString(R.string.error_generic) }
                                        ?: context.getString(R.string.error_generic)
                                }
                            }
                        }
                        loadUrl(request.authorizationUrl)
                    }
                },
                update = { view ->
                    if (view.url == null) {
                        view.loadUrl(request.authorizationUrl)
                    }
                },
            )
            if (webError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.background.copy(alpha = 0.82f))
                        .padding(26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ElevatedPanel {
                        Text(stringResource(R.string.error_generic), style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold)
                        Text(webError.orEmpty(), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.body2, lineHeight = 20.sp)
                        Button(
                            onClick = {
                                webError = null
                                isLoading = true
                                webView?.reload() ?: webView?.loadUrl(request.authorizationUrl)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.dialog_reload))
                        }
                    }
                }
            }
        }
    }
}

private fun pixivLoginCodeOrNull(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val code = uri.getQueryParameter("code") ?: return null
    val isPixivLoginRedirect = uri.scheme == "pixiv" && uri.host == "account" && uri.path == "/login"
    val isPixivCallback = uri.scheme == "https" &&
        uri.host == "app-api.pixiv.net" &&
        uri.path == "/web/v1/users/auth/pixiv/callback"
    val isPixivCodeUrl = uri.host?.contains("pixiv", ignoreCase = true) == true && code.isNotBlank()
    return if (isPixivLoginRedirect || isPixivCallback || isPixivCodeUrl) code else null
}

@Composable
fun ScreenHeader(
    title: String,
    meta: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onActionClick: (() -> Unit)? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onTrailingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actionIcon?.let {
                HeaderIcon(it, onClick = onActionClick)
            }
            if (trailingIcon != null) {
                Spacer(Modifier.width(16.dp))
                HeaderIcon(trailingIcon, onClick = onTrailingClick)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Black,
                style = MiuixTheme.textStyles.title1,
            )
            Text(
                text = meta,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontWeight = FontWeight.Bold,
                style = MiuixTheme.textStyles.main,
            )
        }
    }
}
