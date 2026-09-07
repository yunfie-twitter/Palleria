package com.discord.oauth2rpc

import com.discord.oauth2rpc.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class Rest(
    private val token: String? = null,
    private val client: OkHttpClient = OkHttpClient(),
) {
    enum class Method {
        GET,
        POST,
        PUT,
        PATCH,
        DELETE,
    }

    class Route(
        val method: Method,
        val path: String,
    ) {
        companion object {
            fun get(path: String) = Route(Method.GET, path)

            fun post(path: String) = Route(Method.POST, path)

            fun put(path: String) = Route(Method.PUT, path)

            fun patch(path: String) = Route(Method.PATCH, path)

            fun delete(path: String) = Route(Method.DELETE, path)

            val CURRENT_USER = get("/users/@me")
            val OAUTH2_TOKEN = post("/oauth2/token")
            val OAUTH2_TOKEN_REVOKE = post("/oauth2/token/revoke")
        }
    }

    suspend fun request(
        route: Route,
        body: JSONObject? = null,
        queryParams: Map<String, String>? = null,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            var url = if (route.path.startsWith("http")) route.path else "${Constants.API_BASE_URL}${route.path}"
            if (!queryParams.isNullOrEmpty()) {
                val queryString = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
                url += if (url.contains("?")) "&$queryString" else "?$queryString"
            }

            val requestBuilder = Request.Builder().url(url)
            token?.let {
                val authHeader = if (it.startsWith("Bot ") || it.startsWith("Bearer ")) it else it
                requestBuilder.header("Authorization", authHeader)
            }
            requestBuilder.header("Content-Type", "application/json")

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = body?.toString()?.toRequestBody(mediaType)

            when (route.method) {
                Method.GET -> requestBuilder.get()
                Method.POST -> requestBuilder.post(requestBody ?: "".toRequestBody(mediaType))
                Method.PUT -> requestBuilder.put(requestBody ?: "".toRequestBody(mediaType))
                Method.PATCH -> requestBuilder.patch(requestBody ?: "".toRequestBody(mediaType))
                Method.DELETE -> if (requestBody != null) requestBuilder.delete(requestBody) else requestBuilder.delete()
            }

            val response = client.newCall(requestBuilder.build()).execute()
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $responseBody")
            }
            if (responseBody.isNotBlank()) JSONObject(responseBody) else JSONObject()
        }

    suspend fun getCurrentUser(): User {
        val json = request(Route.CURRENT_USER)
        return User(
            id = json.getString("id"),
            username = json.getString("username"),
            discriminator = json.optString("discriminator").takeIf { it.isNotBlank() },
            globalName = json.optString("global_name").takeIf { it.isNotBlank() },
            avatar = json.optString("avatar").takeIf { it.isNotBlank() },
            bot = json.optBoolean("bot", false),
            flags = json.optInt("flags", 0),
        )
    }

    suspend fun exchangeToken(
        clientId: String,
        clientSecret: String,
        code: String,
        redirectUri: String,
    ): TokenResponse =
        withContext(Dispatchers.IO) {
            val formBody =
                FormBody
                    .Builder()
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("redirect_uri", redirectUri)
                    .build()

            val req =
                Request
                    .Builder()
                    .url("${Constants.API_BASE_URL}/oauth2/token")
                    .post(formBody)
                    .build()

            val response = client.newCall(req).execute()
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IOException("OAuth2 token exchange failed: HTTP ${response.code} $body")
            }
            val json = JSONObject(body)
            TokenResponse(
                accessToken = json.getString("access_token"),
                tokenType = json.optString("token_type", "Bearer"),
                expiresIn = json.optLong("expires_in", 0),
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                scope = json.optString("scope").takeIf { it.isNotBlank() },
            )
        }
}
