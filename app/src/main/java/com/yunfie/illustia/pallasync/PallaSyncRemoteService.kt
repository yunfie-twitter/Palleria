package com.yunfie.illustia.pallasync

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** HTTP boundary for the relay. Protocol parsing remains explicit at each coordinator call site. */
internal class PallaSyncRemoteService(
    private val client: OkHttpClient,
) {
    fun executeUnit(request: Request): PallaSyncHttpResult<Unit> = execute(request) { PallaSyncHttpResult.Success(Unit) }

    fun <T> execute(
        request: Request,
        onSuccess: (Response) -> PallaSyncHttpResult<T>,
    ): PallaSyncHttpResult<T> =
        try {
            client.newCall(request).execute().use { response ->
                val errorBody = if (response.code !in 200..299) response.body?.string() else null
                when (val status = classifyPallaSyncHttpStatus(response.code, errorBody)) {
                    null -> {
                        onSuccess(response)
                    }

                    PallaSyncHttpResult.Gone -> {
                        PallaSyncHttpResult.Gone
                    }

                    is PallaSyncHttpResult.Retryable -> {
                        status
                    }

                    is PallaSyncHttpResult.ProtocolError -> {
                        status
                    }

                    is PallaSyncHttpResult.Success -> {
                        PallaSyncHttpResult.ProtocolError(
                            "Unexpected HTTP status classification",
                            response.code,
                        )
                    }
                }
            }
        } catch (error: IOException) {
            PallaSyncHttpResult.Retryable("PallaSync network request failed: ${error.message}")
        } catch (error: IllegalArgumentException) {
            PallaSyncHttpResult.ProtocolError("PallaSync request was invalid: ${error.message}")
        }
}
