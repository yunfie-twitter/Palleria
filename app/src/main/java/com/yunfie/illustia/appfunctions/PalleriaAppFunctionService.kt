package com.yunfie.illustia.appfunctions

import android.app.appfunctions.AppFunctionException
import android.app.appfunctions.AppFunctionService
import android.app.appfunctions.ExecuteAppFunctionRequest
import android.app.appfunctions.ExecuteAppFunctionResponse
import android.app.appsearch.GenericDocument
import android.content.Intent
import android.content.pm.SigningInfo
import android.net.Uri
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.platform.PlatformCapabilities

/**
 * Exposes Palleria actions to on-device agents through the AppFunctions framework
 * (Android 16 / API 36). Only the service model is used; runtime registration via
 * `AppFunctionManager` is API 37 and intentionally not wired up.
 *
 * Supported functions (see assets/appfunctions/app_functions.xml):
 *  - `openArtwork` — parameter `illustId` (long / string) opens the artwork page.
 *  - `search`      — parameter `query` opens search.
 */
@RequiresApi(PlatformCapabilities.APP_FUNCTIONS_API)
class PalleriaAppFunctionService : AppFunctionService() {
    override fun onExecuteFunction(
        request: ExecuteAppFunctionRequest,
        callingPackage: String,
        callingPackageSigningInfo: SigningInfo,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<ExecuteAppFunctionResponse, AppFunctionException>,
    ) {
        val parameters = request.parameters
        when (request.functionIdentifier) {
            FUNCTION_OPEN_ARTWORK -> {
                val illustId = parameters.longParameter(KEY_ILLUST_ID)
                if (illustId == null || illustId <= 0L) {
                    callback.onError(
                        AppFunctionException(
                            AppFunctionException.ERROR_INVALID_ARGUMENT,
                            "illustId is required",
                        ),
                    )
                    return
                }
                launchArtwork(illustId)
                callback.onResult(EMPTY_RESPONSE)
            }

            FUNCTION_SEARCH -> {
                val query = parameters.stringParameter(KEY_QUERY)
                if (query.isNullOrBlank()) {
                    callback.onError(
                        AppFunctionException(
                            AppFunctionException.ERROR_INVALID_ARGUMENT,
                            "query is required",
                        ),
                    )
                    return
                }
                launchSearch(query)
                callback.onResult(EMPTY_RESPONSE)
            }

            else -> {
                callback.onError(
                    AppFunctionException(
                        AppFunctionException.ERROR_FUNCTION_NOT_FOUND,
                        "Unknown app function: ${request.functionIdentifier}",
                    ),
                )
            }
        }
    }

    private fun launchArtwork(illustId: Long) {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("pixiv://illusts/$illustId"))
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    private fun launchSearch(query: String) {
        startActivity(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, query)
                .setClass(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    private fun GenericDocument?.longParameter(key: String): Long? {
        val document = this ?: return null
        return runCatching { document.getPropertyLong(key) }
            .getOrElse { runCatching { document.getPropertyString(key)?.toLongOrNull() }.getOrNull() }
    }

    private fun GenericDocument?.stringParameter(key: String): String? {
        val document = this ?: return null
        return runCatching { document.getPropertyString(key) }.getOrNull()
    }

    private companion object {
        const val FUNCTION_OPEN_ARTWORK = "openArtwork"
        const val FUNCTION_SEARCH = "search"
        const val KEY_ILLUST_ID = "illustId"
        const val KEY_QUERY = "query"
        const val EMPTY_SCHEMA = "PalleriaAppFunctionResult"
        val EMPTY_RESPONSE = ExecuteAppFunctionResponse(GenericDocument.Builder<GenericDocument.Builder<*>>("", EMPTY_SCHEMA, "").build())
    }
}
