package com.yunfie.illustia.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import com.yunfie.illustia.models.StoredAccount

object PalleriaAccount {
    const val TYPE = "com.yunfie.illustia.account"
    const val AUTHORITY = "com.yunfie.illustia.sync"
    const val USER_ID = "pixiv_user_id"
    const val MANUAL_SYNC = "manual"
    private const val SYNC_INTERVAL_SECONDS = 15L * 60L

    fun requestSync(account: Account, manual: Boolean = true) {
        ContentResolver.requestSync(
            account,
            AUTHORITY,
            Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, manual)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, manual)
                putBoolean(MANUAL_SYNC, manual)
            },
        )
    }

    fun reconcile(context: Context, accounts: List<StoredAccount>) {
        val manager = AccountManager.get(context)
        val expectedIds = accounts.map { it.userId.toString() }.toSet()

        manager.getAccountsByType(TYPE).forEach { systemAccount ->
            val userId = manager.getUserData(systemAccount, USER_ID)
            if (userId !in expectedIds) {
                manager.removeAccountExplicitly(systemAccount)
            }
        }

        accounts.forEach { stored ->
            val userId = stored.userId.toString()
            val existing = manager.getAccountsByType(TYPE).firstOrNull {
                manager.getUserData(it, USER_ID) == userId
            }
            val desiredName = stored.account.ifBlank { stored.name }.ifBlank { userId }
            val account = existing ?: Account(desiredName, TYPE).also {
                manager.addAccountExplicitly(it, null, Bundle().apply { putString(USER_ID, userId) })
            }
            manager.setUserData(account, USER_ID, userId)
            ContentResolver.setIsSyncable(account, AUTHORITY, 1)
            if (existing == null) {
                ContentResolver.setSyncAutomatically(account, AUTHORITY, true)
            }
            ContentResolver.removePeriodicSync(account, AUTHORITY, Bundle.EMPTY)
            ContentResolver.addPeriodicSync(account, AUTHORITY, Bundle.EMPTY, SYNC_INTERVAL_SECONDS)
        }
    }
}
