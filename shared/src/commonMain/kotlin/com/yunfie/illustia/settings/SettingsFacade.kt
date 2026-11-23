package com.yunfie.illustia.settings

import com.yunfie.illustia.models.SavedIllust
import com.yunfie.illustia.models.SavedIllustPage

/**
 * ViewModel 層が設定・セキュリティ・保存作品に対して行う操作の境界。
 *
 * 実体は Android の `SettingsStore`（DataStore / EncryptedSharedPreferences / Room）で、
 * ここでは ViewModel が実際に使う操作だけを公開する。
 */
interface SettingsFacade {
    // --- 保存作品（セーブした画像ライブラリ） ---

    /** 保存済み作品の一覧。 */
    suspend fun getSavedIllusts(): List<SavedIllust>

    /** 保存済み作品の作品 ID。存在しなければ null。 */
    suspend fun getSavedIllustIllustId(illustId: Long): Long?

    /** 作品とページを保存する。 */
    suspend fun saveSavedIllust(
        illust: SavedIllust,
        pages: List<SavedIllustPage>,
    )

    /** 保存済み作品を削除する。 */
    suspend fun deleteSavedIllust(illustId: Long)

    /** 保存作品がディスク上で占めるバイト数。 */
    suspend fun getSavedIllustStorageBytes(): Long

    /** 保存作品のディレクトリの絶対パス。 */
    fun savedIllustDir(): String

    // --- PIN / ロックコード（端末ローカルのみ） ---

    fun hasPinSet(): Boolean

    suspend fun savePinHash(pin: String)

    suspend fun verifyPin(pin: String): Boolean

    fun clearPinHash()

    fun hasUnlockCodeSet(): Boolean

    fun isValidUnlockCode(code: String): Boolean

    suspend fun saveUnlockCodeHash(code: String)

    suspend fun verifyUnlockCode(code: String): Boolean
}
