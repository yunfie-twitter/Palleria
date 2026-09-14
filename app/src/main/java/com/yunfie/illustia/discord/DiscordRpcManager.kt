package com.yunfie.illustia.discord

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.discord.oauth2rpc.Gateway
import com.discord.oauth2rpc.GatewayConnectionState
import com.discord.oauth2rpc.structures.Activity
import com.discord.oauth2rpc.structures.Assets
import com.discord.oauth2rpc.structures.Metadata
import com.discord.oauth2rpc.structures.RichPresence
import com.discord.oauth2rpc.structures.Timestamps
import com.discord.oauth2rpc.utils.ActivityFlags
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.platform.PlatformCapabilities
import com.yunfie.illustia.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages Discord Rich Presence via OAuth2-RPC Gateway.
 *
 * Requirements & Features:
 * - Requires Android 8.1+ (API 27) and > 3GB RAM.
 * - Reuses active WebSocket connection by pushing Opcode 3 (Presence Update) directly
 *   to avoid duplicate socket connections and Discord rate limits.
 * - Uses official "palleria_logo" asset registered in Discord Developer Portal.
 */
class DiscordRpcManager(
    private val appContext: Context? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var updateJob: Job? = null

    private var gateway: Gateway? = null
    private var activeToken: String = ""

    private var sessionStart: Long = System.currentTimeMillis()
    private var currentArtworkId: Long? = null
    private var artworkStart: Long = System.currentTimeMillis()

    fun updatePresence(
        settings: AppSettings,
        selectedIllust: Illust?,
    ) {
        updateJob?.cancel()
        updateJob =
            scope.launch {
                delay(DEBOUNCE_DELAY_MS) // Debounce rapid UI state updates

                mutex.withLock {
                    val token =
                        settings.discordToken
                            .trim()
                            .removeSurrounding("\"")
                            .removeSurrounding("'")

                    if (!isSupported(appContext) || !settings.discordRpcEnabled || token.isBlank()) {
                        recordDiagnostic(
                            when {
                                !isSupported(appContext) -> "開始不可: この端末は Rich Presence の要件を満たしていません"
                                !settings.discordRpcEnabled -> "停止: Rich Presence は無効です"
                                else -> "開始不可: Discord Token が設定されていません"
                            },
                        )
                        tearDown()
                        return@withLock
                    }

                    val appId = settings.discordApplicationId.trim().ifBlank { DEFAULT_APP_ID }
                    val activity = buildActivity(settings, selectedIllust, appId)
                    val richPresence =
                        RichPresence(
                            activities = listOf(activity),
                            afk = false,
                            since = System.currentTimeMillis(),
                            status = "online",
                        )

                    val currentGateway = gateway
                    if (currentGateway != null && activeToken == token && currentGateway.isRunning()) {
                        val sent = currentGateway.setPresence(richPresence)
                        if (sent) {
                            recordDiagnostic("Presence を既存接続へ送信しました: ${activity.details}")
                            return@withLock
                        }
                        recordDiagnostic("既存接続への送信に失敗したため、再接続します")
                    }

                    launchNewGateway(token, appId, richPresence)
                }
            }
    }

    private fun buildActivity(
        settings: AppSettings,
        selectedIllust: Illust?,
        appId: String,
    ): Activity {
        val showDetails = settings.discordRpcShowArtworkDetails
        val showButtons = settings.discordRpcShowButtons
        val (detailText, stateText, startTimestamp) =
            if (selectedIllust != null) {
                if (currentArtworkId != selectedIllust.id) {
                    currentArtworkId = selectedIllust.id
                    artworkStart = System.currentTimeMillis()
                }
                val details = if (showDetails) selectedIllust.title.take(128).ifBlank { "作品を閲覧中" } else "作品を閲覧中"
                val state = if (showDetails) "by ${selectedIllust.artistName}".take(128).ifBlank { "Palleria" } else "Palleria"
                Triple(details, state, artworkStart)
            } else {
                currentArtworkId = null
                Triple("イラストを閲覧中", "Palleria", sessionStart)
            }

        val buttons =
            if (showButtons) {
                if (selectedIllust != null && showDetails) {
                    listOf(BUTTON_PIXIV_LABEL, BUTTON_DOWNLOAD_LABEL)
                } else {
                    listOf(BUTTON_DOWNLOAD_LABEL)
                }
            } else {
                null
            }

        val metadata =
            if (showButtons) {
                if (selectedIllust != null && showDetails) {
                    Metadata(
                        buttonUrls = listOf("https://www.pixiv.net/artworks/${selectedIllust.id}", DOWNLOAD_URL),
                    )
                } else {
                    Metadata(buttonUrls = listOf(DOWNLOAD_URL))
                }
            } else {
                null
            }

        return Activity(
            applicationId = appId,
            name = "Palleria",
            details = detailText,
            state = stateText,
            type = 0,
            timestamps = Timestamps(start = startTimestamp, end = null),
            assets =
                Assets(
                    largeImage = "palleria_logo",
                    largeText = "Palleria",
                    smallImage = null,
                    smallText = null,
                ),
            flags = ActivityFlags.INSTANCE,
            buttons = buttons,
            metadata = metadata,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun launchNewGateway(
        token: String,
        appId: String,
        richPresence: RichPresence,
    ) {
        // Spin up new client if token changed or connection not active
        tearDown()
        recordDiagnostic("Discord Gateway に接続を開始しました (Application ID: $appId)")
        try {
            val newGateway = Gateway(token = token, scope = scope)
            gateway = newGateway
            activeToken = token

            newGateway.onReady = { readyEvent ->
                recordDiagnostic("Discord Gateway に接続し、Presence を送信しました (${readyEvent.user.username})")
            }
            newGateway.onClose = { code, reason ->
                recordDiagnostic("Discord Gateway から切断されました (Code: $code, Reason: $reason)")
            }
            newGateway.onError = { error ->
                recordDiagnostic("Discord Gateway エラー: ${error.safeDiagnosticMessage()}")
            }

            newGateway.connect(richPresence)

            // Wait for connection or premature error
            val readyState =
                withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                    newGateway.connectionState.firstOrNull {
                        it == GatewayConnectionState.READY ||
                            it == GatewayConnectionState.CLOSED ||
                            it == GatewayConnectionState.ERROR
                    }
                }

            if (readyState == GatewayConnectionState.READY) {
                // Successfully connected and presence sent
            } else if (readyState == GatewayConnectionState.ERROR || readyState == GatewayConnectionState.CLOSED) {
                recordDiagnostic("Discord 接続が拒否または切断されました (Token を確認してください)")
                tearDown()
            } else {
                recordDiagnostic("接続がタイムアウトしました。Token、ネットワーク、Discord 側の制限を確認してください")
                tearDown()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Failed to start Discord Gateway", error)
            recordDiagnostic("Discord 接続エラー: ${error.safeDiagnosticMessage()}")
            tearDown()
        }
    }

    fun close() {
        updateJob?.cancel()
        scope.launch {
            mutex.withLock {
                tearDown()
            }
        }
    }

    private fun tearDown() {
        runCatching {
            gateway?.disconnect()
        }
        gateway = null
        activeToken = ""
        currentArtworkId = null
    }

    private fun recordDiagnostic(message: String) {
        recordDiagnosticStatic(message)
    }

    private fun Throwable.safeDiagnosticMessage(): String =
        message
            ?.replace(Regex("(?i)(token|authorization)\\s*[:=]\\s*[^,\\s]+"), "$1=[redacted]")
            ?.take(MAX_ERROR_MESSAGE_LENGTH)
            ?: javaClass.simpleName

    companion object {
        private const val TAG = "DiscordRpcManager"
        const val DEFAULT_APP_ID = "1544652855233744926"
        const val DOWNLOAD_URL = "https://yunfi.f5.si/Palleria/user/installation"
        const val BUTTON_PIXIV_LABEL = "Pixivで見る"
        const val BUTTON_DOWNLOAD_LABEL = "Palleriaをダウンロード"
        private const val DEBOUNCE_DELAY_MS = 600L
        private const val CONNECTION_TIMEOUT_MS = 15_000L
        private const val MAX_DIAGNOSTIC_ENTRIES = 20
        private const val MAX_ERROR_MESSAGE_LENGTH = 240
        private val DIAGNOSTIC_TIME_FORMAT = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        private val _diagnostics = MutableStateFlow<List<String>>(emptyList())
        val diagnostics: StateFlow<List<String>> = _diagnostics.asStateFlow()

        fun clearDiagnostics() {
            _diagnostics.value = emptyList()
        }

        fun isSupported(context: Context? = null): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return false
            return context == null || hasSufficientRam(context)
        }

        fun hasSufficientRam(context: Context): Boolean = !PlatformCapabilities.isLowRamDevice(context)

        private fun recordDiagnosticStatic(message: String) {
            val entry = "${DIAGNOSTIC_TIME_FORMAT.format(Date())}  $message"
            Log.d(TAG, entry)
            _diagnostics.value = (_diagnostics.value + entry).takeLast(MAX_DIAGNOSTIC_ENTRIES)
        }
    }
}
