package com.yunfie.illustia.settings

import com.yunfie.illustia.data.PersistedAuth
import com.yunfie.illustia.data.RepositoryPersistence
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.MAX_SESSION_LIFETIME_SECONDS
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.models.SESSION_EXPIRY_SKEW_MILLIS
import com.yunfie.illustia.models.SavedIllust
import com.yunfie.illustia.models.SavedIllustPage
import com.yunfie.illustia.platform.FileSystem
import com.yunfie.illustia.platform.currentTimeMillis
import com.yunfie.illustia.platform.sha256Hex
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/*
 * iOS 側の設定永続化。
 *
 * Android は DataStore + EncryptedSharedPreferences + Room に分散しているが、iOS では
 * NSUserDefaults を 1 つ使い、設定全体を 1 つの JSON blob として保存する。機密値
 * （リフレッシュトークン・OAuth セッション）は keychain 相当として別キーへ置き、設定本体
 * （`AppSettings.refreshToken` を含む）からは読み書き時に差し替える。
 *
 * PIN / ロックコードは端末ローカルのダイジェストのみを保存する。設定 UI は Android と同じ
 * 契約（[SettingsFacade]）を見るので、画面側に分岐は要らない。
 */

private const val KEY_SETTINGS_JSON = "illustia.settings.v1"
private const val KEY_REFRESH_TOKEN = "illustia.secure.refreshToken"
private const val KEY_CACHED_SESSION = "illustia.secure.oauthSession"
private const val KEY_PIN_HASH = "illustia.secure.pinHash"
private const val KEY_UNLOCK_CODE_HASH = "illustia.secure.unlockCodeHash"
private const val KEY_SAVED_ILLUSTS = "illustia.savedIllusts.v1"
private const val KEY_STARTUP_ACCOUNT_HASH = "illustia.startup.accountHash"

/** keychain 相当の置き場。NSUserDefaults の別キーに隔離する。 */
private class SecureBox(
    private val defaults: NSUserDefaults,
) {
    fun string(key: String): String? = defaults.stringForKey(key)?.takeIf { it.isNotBlank() }

    fun put(
        key: String,
        value: String?,
    ) {
        if (value.isNullOrBlank()) {
            defaults.removeObjectForKey(key)
        } else {
            defaults.setObject(value, key)
        }
    }
}

@Serializable
private data class PersistedSessionJson(
    val access: String,
    val refresh: String,
    val user: Long? = null,
    val expires: Long = 0L,
)

@Serializable
private data class SavedIllustJson(
    val illustId: Long,
    val title: String,
    val artistName: String,
    val artistId: Long,
    val thumbUrl: String,
    val localCoverPath: String? = null,
    val localPagePathsJson: String,
    val pageCount: Int,
    val savedAt: Long,
    val saveGroup: String,
    val xRestrict: Int,
)

class IosSettingsStore(
    private val fileSystem: FileSystem,
    private val cacheDir: String,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SettingsFacade,
    RepositoryPersistence {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val secure = SecureBox(defaults)
    private val savedIllustsSerializer = ListSerializer(SavedIllustJson.serializer())

    // --- 設定本体 ---------------------------------------------------------

    override suspend fun readSettings(viewHistoryLimit: Int?): AppSettings = loadSettings(viewHistoryLimit)

    override suspend fun readStartupSettings(): AppSettings = loadSettings(STARTUP_VIEW_HISTORY_LIMIT)

    override suspend fun readFeedSettings(): AppSettings = loadSettings(viewHistoryLimit = 0)

    override suspend fun readFullViewHistory(): List<Illust> = loadSettings(null).viewHistory

    override suspend fun readAuth(): PersistedAuth {
        val settings = loadSettings(viewHistoryLimit = 0)
        return PersistedAuth(refreshToken = settings.refreshToken, networkMode = settings.pixivNetworkMode)
    }

    override suspend fun writeSettings(
        settings: AppSettings,
        baseSettings: AppSettings?,
    ): AppSettings {
        // The refresh token lives in secure storage, never in the settings blob.
        secure.put(KEY_REFRESH_TOKEN, settings.refreshToken.takeIf { it.isNotBlank() && it != STARTUP_TOKEN_PLACEHOLDER })
        val stored = settings.copy(refreshToken = "")
        defaults.setObject(IosSettingsCodec.encode(stored), KEY_SETTINGS_JSON)
        return settings
    }

    override suspend fun persistAuth(session: PixivSession) {
        secure.put(KEY_REFRESH_TOKEN, session.refreshToken)
        val payload =
            PersistedSessionJson(
                access = session.accessToken,
                refresh = session.refreshToken,
                user = session.userId,
                expires = session.expiresAtMillis,
            )
        secure.put(KEY_CACHED_SESSION, json.encodeToString(PersistedSessionJson.serializer(), payload))
        secure.put(KEY_STARTUP_ACCOUNT_HASH, session.refreshToken.sha256Hex())
    }

    override suspend fun clearSensitive() {
        secure.put(KEY_REFRESH_TOKEN, null)
        secure.put(KEY_CACHED_SESSION, null)
        secure.put(KEY_STARTUP_ACCOUNT_HASH, null)
        // Saved artwork references no longer point at a signed-in account.
        defaults.removeObjectForKey(KEY_SAVED_ILLUSTS)
    }

    override fun readStartupAccountHash(): String {
        val cached = secure.string(KEY_STARTUP_ACCOUNT_HASH)
        if (!cached.isNullOrBlank()) return cached
        val token = secure.string(KEY_REFRESH_TOKEN) ?: return ""
        val hash = token.sha256Hex()
        secure.put(KEY_STARTUP_ACCOUNT_HASH, hash)
        return hash
    }

    override fun homeCacheDirectory(): String = "$cacheDir/home-feed"

    /**
     * 起動時の OAuth セッション。期限切れ・別アカウント・ありえない寿命なら null。
     *
     * `requireSession()` がこの値を使うと、毎回リフレッシュトークンでログインし直さずに済む。
     */
    fun cachedSession(): PixivSession? {
        val refreshToken = secure.string(KEY_REFRESH_TOKEN) ?: return null
        val raw = secure.string(KEY_CACHED_SESSION) ?: return null
        return runCatching {
            val parsed = json.decodeFromString(PersistedSessionJson.serializer(), raw)
            val now = currentTimeMillis()
            if (parsed.refresh != refreshToken) return null
            if (parsed.expires <= now + SESSION_EXPIRY_SKEW_MILLIS) return null
            if (parsed.expires > now + MAX_SESSION_LIFETIME_SECONDS * 1000) return null
            if (parsed.access.isBlank()) return null
            PixivSession(parsed.access, refreshToken, parsed.user?.takeIf { it > 0 }, parsed.expires)
        }.getOrNull()
    }

    // --- 保存作品 ---------------------------------------------------------

    override suspend fun getSavedIllusts(): List<SavedIllust> = loadSavedIllusts().map { it.toModel() }

    override suspend fun getSavedIllustIllustId(illustId: Long): Long? =
        loadSavedIllusts().firstOrNull { it.illustId == illustId }?.illustId

    override suspend fun saveSavedIllust(
        illust: SavedIllust,
        pages: List<SavedIllustPage>,
    ) {
        val existing = loadSavedIllusts().filterNot { it.illustId == illust.illustId }
        val pagePaths = json.encodeToString(ListSerializer(String.serializer()), pages.sortedBy { it.pageIndex }.map { it.localPath })
        val entry =
            SavedIllustJson(
                illustId = illust.illustId,
                title = illust.title,
                artistName = illust.artistName,
                artistId = illust.artistId,
                thumbUrl = illust.thumbUrl,
                localCoverPath = illust.localCoverPath,
                localPagePathsJson = pagePaths,
                pageCount = illust.pageCount,
                savedAt = illust.savedAt,
                saveGroup = illust.saveGroup,
                xRestrict = illust.xRestrict,
            )
        persistSavedIllusts(existing + entry)
    }

    override suspend fun deleteSavedIllust(illustId: Long) {
        persistSavedIllusts(loadSavedIllusts().filterNot { it.illustId == illustId })
    }

    override suspend fun getSavedIllustStorageBytes(): Long {
        val directory = savedIllustDir()
        return fileSystem
            .list(directory)
            .sumOf { name -> fileSystem.stat("$directory/$name")?.takeIf { !it.isDirectory }?.size ?: 0L }
    }

    override fun savedIllustDir(): String = "$cacheDir/saved"

    // --- PIN / ロックコード -----------------------------------------------

    override fun hasPinSet(): Boolean = !secure.string(KEY_PIN_HASH).isNullOrBlank()

    override suspend fun savePinHash(pin: String) {
        secure.put(KEY_PIN_HASH, pin.sha256Hex())
    }

    override suspend fun verifyPin(pin: String): Boolean {
        val stored = secure.string(KEY_PIN_HASH) ?: return false
        return stored == pin.sha256Hex()
    }

    override fun clearPinHash() {
        secure.put(KEY_PIN_HASH, null)
    }

    override fun hasUnlockCodeSet(): Boolean = !secure.string(KEY_UNLOCK_CODE_HASH).isNullOrBlank()

    override fun isValidUnlockCode(code: String): Boolean = code.length in 4..12 && code.all { it.isLetterOrDigit() }

    override suspend fun saveUnlockCodeHash(code: String) {
        secure.put(KEY_UNLOCK_CODE_HASH, code.sha256Hex())
    }

    override suspend fun verifyUnlockCode(code: String): Boolean {
        val stored = secure.string(KEY_UNLOCK_CODE_HASH) ?: return false
        return stored == code.sha256Hex()
    }

    // --- 内部 -------------------------------------------------------------

    private fun loadSettings(viewHistoryLimit: Int?): AppSettings {
        val stored =
            runCatching {
                defaults.stringForKey(KEY_SETTINGS_JSON)?.let { IosSettingsCodec.decode(it) }
            }.getOrNull() ?: AppSettings()
        val withToken = stored.copy(refreshToken = secure.string(KEY_REFRESH_TOKEN).orEmpty())
        return viewHistoryLimit?.let { withToken.copy(viewHistory = withToken.viewHistory.take(it)) } ?: withToken
    }

    private fun loadSavedIllusts(): List<SavedIllustJson> {
        val raw = defaults.stringForKey(KEY_SAVED_ILLUSTS) ?: return emptyList()
        return runCatching { json.decodeFromString(savedIllustsSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun persistSavedIllusts(entries: List<SavedIllustJson>) {
        defaults.setObject(json.encodeToString(savedIllustsSerializer, entries), KEY_SAVED_ILLUSTS)
    }

    private fun SavedIllustJson.toModel(): SavedIllust =
        SavedIllust(
            illustId = illustId,
            title = title,
            artistName = artistName,
            artistId = artistId,
            thumbUrl = thumbUrl,
            localCoverPath = localCoverPath,
            localPagePathsJson = localPagePathsJson,
            pageCount = pageCount,
            savedAt = savedAt,
            saveGroup = saveGroup,
            xRestrict = xRestrict,
        )
}

/**
 * `AppSettings` の JSON コーデック。
 *
 * `AppSettings` は `@Serializable` にしてあるので、ここは既定値の補完と未知キーの無視だけを
 * 担う。リフレッシュトークンは keychain 相当の別キーへ逃がすため、blob へは常に空で書く。
 */
internal object IosSettingsCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun encode(settings: AppSettings): String = json.encodeToString(AppSettings.serializer(), settings.copy(refreshToken = ""))

    fun decode(raw: String): AppSettings = json.decodeFromString(AppSettings.serializer(), raw)
}
