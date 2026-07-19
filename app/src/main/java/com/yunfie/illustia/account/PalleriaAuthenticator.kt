package com.yunfie.illustia.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.accounts.NetworkErrorException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.yunfie.illustia.MainActivity

class PalleriaAuthenticator(private val context: Context) : AbstractAccountAuthenticator(context) {
    private fun loginIntent(response: AccountAuthenticatorResponse) =
        Intent(context, MainActivity::class.java).apply {
            putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    override fun addAccount(
        response: AccountAuthenticatorResponse,
        accountType: String,
        authTokenType: String?,
        requiredFeatures: Array<out String>?,
        options: Bundle?,
    ) = Bundle().apply { putParcelable(AccountManager.KEY_INTENT, loginIntent(response)) }

    override fun editProperties(response: AccountAuthenticatorResponse, accountType: String) =
        Bundle().apply { putParcelable(AccountManager.KEY_INTENT, loginIntent(response)) }

    override fun getAuthToken(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String,
        options: Bundle?,
    ) = Bundle().apply {
        putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION)
        putString(AccountManager.KEY_ERROR_MESSAGE, "Authentication tokens are managed inside Palleria")
    }

    override fun getAuthTokenLabel(authTokenType: String) = "Palleria"

    override fun confirmCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        options: Bundle?,
    ): Bundle? = null

    override fun updateCredentials(
        response: AccountAuthenticatorResponse,
        account: Account,
        authTokenType: String?,
        options: Bundle?,
    ) = Bundle().apply { putParcelable(AccountManager.KEY_INTENT, loginIntent(response)) }

    override fun hasFeatures(
        response: AccountAuthenticatorResponse,
        account: Account,
        features: Array<out String>,
    ) = Bundle().apply { putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false) }
}
