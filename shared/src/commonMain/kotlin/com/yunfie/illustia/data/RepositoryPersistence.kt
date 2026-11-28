package com.yunfie.illustia.data

import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.PageResult
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.platform.FileSystem
import com.yunfie.illustia.platform.sha256Hex
import com.yunfie.illustia.settings.AppSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/*
 * In-memory-safe platform contract for the repository's persistence half.
 *
 * The ViewModel layer needs settings, credentials and a disposable home-feed snapshot. Android
 * keeps its DataStore/EncryptedSharedPreferences/Room implementation; iOS supplies an
 * NSUserDefaults-backed one. Nothing here is allowed to leak a platform type.
 */

/** 認証だけを読む射影。 */
data class PersistedAuth(
    val refreshToken: String,
    val networkMode: String,
)

/**
 * リフレッシュトークンからアカウント識別ハッシュを作る。
 *
 * トークンそのものをファイル名やスナップショットへ書かないための不可逆ハッシュ。
 */
fun computeAccountKey(token: String): String = token.sha256Hex()

interface RepositoryPersistence {
    suspend fun readSettings(viewHistoryLimit: Int? = null): AppSettings

    suspend fun readFullViewHistory(): List<Illust>

    suspend fun readStartupSettings(): AppSettings

    /** フィード表示に必要な最小限の設定（Room / 履歴デコードを通さない）。 */
    suspend fun readFeedSettings(): AppSettings

    /** 認証情報だけを読む。 */
    suspend fun readAuth(): PersistedAuth

    /** 保存後の設定を返す。 */
    suspend fun writeSettings(
        settings: AppSettings,
        baseSettings: AppSettings? = null,
    ): AppSettings

    suspend fun persistAuth(session: PixivSession)

    suspend fun clearSensitive()

    /** 起動時のログイン判定に使うアカウントハッシュ。 */
    fun readStartupAccountHash(): String

    /** ホームフィードのスナップショットを置くディレクトリの絶対パス。 */
    fun homeCacheDirectory(): String
}

/**
 * ホームフィードの使い捨てスナップショット。
 *
 * 認証情報は含まず、アカウントハッシュ・保存時刻・先頭ページだけを持つ。古い／別アカウントの
 * スナップショットは黙って捨てる。`java.io.AtomicFile` の代わりに [FileSystem] 越しに
 * 一時ファイルへ書いてから置き換える。
 */
class HomeFeedSnapshotStore(
    private val fileSystem: FileSystem,
    directory: String,
    private val now: () -> Long,
) {
    @Serializable
    private data class Snapshot(
        val account: String,
        val savedAt: Long,
        val items: List<Illust>,
    )

    private val directory = directory.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true }
    private val illustrations = ListSerializer(Illust.serializer())

    fun readWithAccountHash(
        kind: HomeFeedKind,
        accountHash: String,
    ): PageResult<Illust>? {
        if (accountHash.isBlank()) return null
        val path = pathFor(kind)
        val entry = fileSystem.stat(path) ?: return null
        if (entry.isDirectory || entry.size <= 0L || entry.size > MAX_BYTES) return null
        val text = fileSystem.readText(path) ?: return null
        return runCatching {
            val snapshot = json.decodeFromString(Snapshot.serializer(), text)
            val current = now()
            if (snapshot.account != accountHash) return@runCatching null
            if (current - snapshot.savedAt !in 0..MAX_AGE) return@runCatching null
            // Pagination belongs to the fresh response, not yesterday's first page.
            PageResult(snapshot.items.take(MAX_ITEMS), null)
        }.getOrNull()
    }

    fun write(
        kind: HomeFeedKind,
        token: String,
        page: PageResult<Illust>,
    ) {
        if (token.isBlank()) return
        runCatching {
            val payload = json.encodeToString(Snapshot.serializer(), Snapshot(computeAccountKey(token), now(), page.items.take(MAX_ITEMS)))
            if (payload.length > MAX_BYTES) return
            fileSystem.makeDirectories(directory)
            val target = pathFor(kind)
            val staging = fileSystem.createTempFile(prefix = "home-${kind.name}", suffix = ".tmp", directory = directory)
            fileSystem.openWrite(staging).use { it.write(payload.encodeToByteArray()) }
            fileSystem.copy(staging, target)
            fileSystem.delete(staging)
        }
    }

    fun clear() {
        runCatching {
            fileSystem
                .list(directory)
                .filter { it.startsWith("home-") }
                .forEach { fileSystem.delete("$directory/$it") }
        }
    }

    private fun pathFor(kind: HomeFeedKind): String = "$directory/${kind.name}.json"

    private companion object {
        const val MAX_ITEMS = 60
        const val MAX_BYTES = 2 * 1024 * 1024
        const val MAX_AGE = 24 * 60 * 60 * 1000L
    }
}
