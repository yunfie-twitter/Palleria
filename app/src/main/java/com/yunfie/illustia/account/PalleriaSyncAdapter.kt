package com.yunfie.illustia.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import com.yunfie.illustia.data.PixivApiClient
import com.yunfie.illustia.models.NetworkMode
import com.yunfie.illustia.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class PalleriaSyncAdapter(context: Context) : AbstractThreadedSyncAdapter(context, true) {
    override fun onPerformSync(
        account: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult,
    ) {
        runBlocking(Dispatchers.IO) {
            runCatching {
                val settingsStore = SettingsStore(context)
                val settings = settingsStore.read()
                PalleriaAccount.reconcile(context, settings.accounts)
                val userId = AccountManager.get(context)
                    .getUserData(account, PalleriaAccount.USER_ID)
                    ?.toLongOrNull()
                    ?: error("同期対象のPixivユーザーIDがありません。")
                val storedAccount = settings.accounts.firstOrNull { it.userId == userId }
                    ?: error("同期対象のPixivアカウントが見つかりません。")

                val api = PixivApiClient(NetworkMode.fromCode(settings.pixivNetworkMode))
                val session = api.loginWithRefreshToken(storedAccount.refreshToken)
                api.notifications(session)
            }.onFailure {
                syncResult.stats.numIoExceptions++
            }
        }
    }
}
