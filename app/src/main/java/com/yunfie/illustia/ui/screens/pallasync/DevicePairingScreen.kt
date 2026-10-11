package com.yunfie.illustia.ui.screens.pallasync

import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val WHITESPACE_REGEX = Regex("\\s+")

@Composable
fun DevicePairingScreen(
    serverUrl: String,
    onBack: () -> Unit,
    onPairSuccess: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { PalleriaSyncManager(context = context) }
    val clipboardManager = LocalClipboardManager.current

    var seedPhrase by remember { mutableStateOf("") }
    var enteredSeedPhrase by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Per PallaSync Protocol 3.0 §5.5 (C-02, Zero-Persistence Rule),
        // mnemonic seed phrases are never persisted to disk.
    }

    val qrLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                enteredSeedPhrase = result.contents
            }
        }

    val tabs =
        listOf(
            stringResource(R.string.pallasync_view_sync_code),
            stringResource(R.string.pallasync_enter_sync_code),
        )
    var selectedTabIndex by remember { mutableStateOf(0) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.pallasync_sync_devices),
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface)
                    .padding(scaffoldPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            TabRowWithContour(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTabIndex == 0) {
                // Generate QR code bitmap for the seed phrase
                val qrBitmap =
                    remember(seedPhrase) {
                        try {
                            val writer = QRCodeWriter()
                            val bitMatrix = writer.encode(seedPhrase, BarcodeFormat.QR_CODE, 256, 256)
                            val width = bitMatrix.width
                            val height = bitMatrix.height
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                            for (x in 0 until width) {
                                for (y in 0 until height) {
                                    bmp.setPixel(
                                        x,
                                        y,
                                        if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
                                    )
                                }
                            }
                            bmp
                        } catch (_: WriterException) {
                            null
                        } catch (_: IllegalArgumentException) {
                            null
                        }
                    }
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.pallasync_sync_qr_code),
                        modifier = Modifier.size(200.dp).padding(bottom = 16.dp),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.pallasync_recovery_phrase_title),
                        style = MiuixTheme.textStyles.headline1,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.pallasync_recovery_phrase_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    if (seedPhrase.isNotEmpty()) {
                        val words = seedPhrase.trim().split(WHITESPACE_REGEX)
                        if (words.size == 24) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                itemsIndexed(words) { index, word ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .background(
                                                    MiuixTheme.colorScheme.surfaceVariant,
                                                    shape =
                                                        androidx.compose.foundation.shape
                                                            .RoundedCornerShape(8.dp),
                                                ).padding(horizontal = 8.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.width(24.dp),
                                        )
                                        Text(
                                            text = word,
                                            style = MiuixTheme.textStyles.body1,
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(seedPhrase))
                                    Toast.makeText(context, R.string.msg_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.action_copy_to_clipboard))
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.pallasync_invalid_seed_phrase),
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.status_loading),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                // Enter Sync Code
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.pallasync_join_chain),
                        style = MiuixTheme.textStyles.headline1,
                        color = MiuixTheme.colorScheme.onBackground,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.pallasync_join_chain_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    TextField(
                        value = enteredSeedPhrase,
                        onValueChange = { enteredSeedPhrase = it },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = {
                            if (enteredSeedPhrase.trim().split(WHITESPACE_REGEX).size == 24) {
                                isJoining = true
                                scope.launch {
                                    val success =
                                        try {
                                            syncManager.joinChain(
                                                seedPhrase = enteredSeedPhrase.trim(),
                                                serverUrl = serverUrl,
                                            )
                                        } finally {
                                            isJoining = false
                                        }
                                    if (success) {
                                        Toast.makeText(context, R.string.msg_pallasync_chain_joined, Toast.LENGTH_SHORT).show()
                                        onPairSuccess()
                                    } else {
                                        Toast.makeText(context, R.string.error_pallasync_join_chain_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, R.string.error_pallasync_seed_phrase_word_count, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isJoining,
                    ) {
                        Text(
                            stringResource(
                                if (isJoining) R.string.pallasync_joining else R.string.pallasync_join_chain,
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            qrLauncher.launch(
                                ScanOptions().apply {
                                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    setPrompt(context.getString(R.string.pallasync_scan_qr_prompt))
                                    setBeepEnabled(false)
                                    setBarcodeImageEnabled(false)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            top.yukonga.miuix.kmp.basic.ButtonDefaults
                                .buttonColorsPrimary(),
                    ) {
                        Text(stringResource(R.string.pallasync_scan_qr_code))
                    }
                }
            }
        }
    }
}
