package com.yunfie.illustia.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.data.isDiscordAppUrl
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.ui.screens.discord.LocalWebLoginContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONTokener
import com.yunfie.illustia.ui.screens.discord.DiscordLoginScreen as SharedDiscordLoginScreen

private const val JS_SNIPPET =
    "(function(){var i=document.createElement('iframe');document.body.appendChild(i);" +
        "try{return JSON.parse(i.contentWindow.localStorage.getItem('token'));}finally{i.remove();}})()"

private const val MOTOROLA = "motorola"
private const val SAMSUNG_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DiscordLoginScreen(
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var completed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            completed = true
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    CompositionLocalProvider(
        LocalWebLoginContent provides
            { modifier, _ ->
                AndroidView(
                    modifier = modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams =
                                ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )

                            settings.allowContentAccess = false
                            settings.allowFileAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            // Motorola 端末では UA 解析が Discord ログインを壊すため差し替える。
                            if (Build.MANUFACTURER.equals(MOTOROLA, ignoreCase = true)) {
                                settings.userAgentString = SAMSUNG_USER_AGENT
                            }

                            webViewClient =
                                object : WebViewClient() {
                                    override fun onPageFinished(
                                        view: WebView,
                                        url: String?,
                                    ) {
                                        super.onPageFinished(view, url)
                                        captureToken(view, url)
                                    }

                                    override fun doUpdateVisitedHistory(
                                        view: WebView,
                                        url: String?,
                                        isReload: Boolean,
                                    ) {
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                        captureToken(view, url)
                                    }

                                    private fun captureToken(
                                        view: WebView,
                                        url: String?,
                                    ) {
                                        if (!completed && isDiscordAppUrl(url) && isDiscordAppUrl(view.url)) {
                                            view.evaluateJavascript(JS_SNIPPET) { encodedToken ->
                                                if (completed || !isDiscordAppUrl(view.url)) return@evaluateJavascript
                                                val token =
                                                    runCatching { JSONTokener(encodedToken).nextValue() as? String }.getOrNull()
                                                if (!token.isNullOrBlank() && token != "null" && token != "undefined") {
                                                    completed = true
                                                    viewModel.updateDiscordToken(token)
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            AppStrings.get(StringKey.DiscordLoginSuccess),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    scope.launch(Dispatchers.Main) { onBack() }
                                                }
                                            }
                                        }
                                    }
                                }

                            webViewInstance = this
                            loadUrl("https://discord.com/login")
                        }
                    },
                )
            },
    ) {
        SharedDiscordLoginScreen(onBack = onBack)
    }
}
