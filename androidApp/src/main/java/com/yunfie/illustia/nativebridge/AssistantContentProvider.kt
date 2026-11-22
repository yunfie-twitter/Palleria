package com.yunfie.illustia.nativebridge

import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Builds the semantic description handed to the Assistant through
 * `Activity.onProvideAssistContent`.
 *
 * Kept free of Android framework types so the JSON-LD can be verified by unit tests.
 * The JSON is produced with kotlinx.serialization so titles and tags are escaped correctly.
 */
object AssistantContentProvider {
    private val json = Json

    /** Nothing is exposed while the app is locked or in privacy mode. */
    fun resolve(state: IllustiaUiState): AssistantSnapshot? {
        if (state.appLocked || state.privacyLocked) return null
        return when {
            state.selectedIllust != null -> {
                val illust = state.selectedIllust
                AssistantSnapshot(webUri = artworkUrl(illust.id), structuredData = artworkJson(illust))
            }

            state.showUserPage && state.selectedUser != null -> {
                val user = state.selectedUser
                AssistantSnapshot(webUri = userUrl(user.id), structuredData = userJson(user))
            }

            else -> {
                AssistantSnapshot(webUri = HOME_URL, structuredData = null)
            }
        }
    }

    fun artworkUrl(id: Long): String = "https://www.pixiv.net/artworks/$id"

    fun userUrl(id: Long): String = "https://www.pixiv.net/users/$id"

    private fun artworkJson(illust: Illust): String {
        val payload =
            buildJsonObject {
                put("@context", "https://schema.org")
                put("@type", "ImageObject")
                put("name", illust.title)
                put("url", artworkUrl(illust.id))
                if (illust.artistName.isNotBlank()) {
                    putJsonObject("creator") {
                        put("@type", "Person")
                        put("name", illust.artistName)
                    }
                }
                if (illust.tags.isNotEmpty()) {
                    put("keywords", illust.tags.joinToString(","))
                }
            }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun userJson(user: UserProfile): String {
        val payload =
            buildJsonObject {
                put("@context", "https://schema.org")
                put("@type", "Person")
                put("name", user.name)
                put("url", userUrl(user.id))
                if (user.account.isNotBlank()) put("alternateName", user.account)
                if (user.comment.isNotBlank()) put("description", user.comment)
            }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private const val HOME_URL = "https://www.pixiv.net/"
}

data class AssistantSnapshot(
    val webUri: String,
    val structuredData: String?,
)
