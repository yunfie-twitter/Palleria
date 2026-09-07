package com.discord.oauth2rpc.structures

import com.discord.oauth2rpc.utils.ActivityFlags
import com.discord.oauth2rpc.utils.Constants
import org.json.JSONArray
import org.json.JSONObject

data class Timestamps(
    val start: Long? = null,
    val end: Long? = null,
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        start?.let { json.put("start", it) }
        end?.let { json.put("end", it) }
        return json
    }
}

data class Assets(
    val largeImage: String? = null,
    val largeText: String? = null,
    val smallImage: String? = null,
    val smallText: String? = null,
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        largeImage?.let { json.put("large_image", it) }
        largeText?.let { json.put("large_text", it) }
        smallImage?.let { json.put("small_image", it) }
        smallText?.let { json.put("small_text", it) }
        return json
    }
}

data class Party(
    val id: String? = null,
    val currentSize: Int? = null,
    val maxSize: Int? = null,
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        id?.let { json.put("id", it) }
        if (currentSize != null && maxSize != null) {
            val sizeArray = JSONArray()
            sizeArray.put(currentSize)
            sizeArray.put(maxSize)
            json.put("size", sizeArray)
        }
        return json
    }
}

data class Secrets(
    val join: String? = null,
    val spectate: String? = null,
    val match: String? = null,
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        join?.let { json.put("join", it) }
        spectate?.let { json.put("spectate", it) }
        match?.let { json.put("match", it) }
        return json
    }
}

data class Button(
    val label: String,
    val url: String,
)

data class Metadata(
    val buttonUrls: List<String> = emptyList(),
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        val arr = JSONArray()
        buttonUrls.forEach { arr.put(it) }
        json.put("button_urls", arr)
        return json
    }
}

data class Activity(
    val applicationId: String? = null,
    val name: String,
    val details: String? = null,
    val state: String? = null,
    val type: Int = Constants.ActivityType.PLAYING,
    val timestamps: Timestamps? = null,
    val assets: Assets? = null,
    val party: Party? = null,
    val secrets: Secrets? = null,
    val buttons: List<String>? = null,
    val metadata: Metadata? = null,
    val flags: Long? = ActivityFlags.INSTANCE,
    val url: String? = null,
) {
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        applicationId?.let { json.put("application_id", it) }
        json.put("name", name)
        details?.let { json.put("details", it) }
        state?.let { json.put("state", it) }
        json.put("type", type)
        timestamps?.let { json.put("timestamps", it.toJSONObject()) }
        assets?.let { json.put("assets", it.toJSONObject()) }
        party?.let { json.put("party", it.toJSONObject()) }
        secrets?.let { json.put("secrets", it.toJSONObject()) }
        buttons?.let { btnList ->
            val arr = JSONArray()
            btnList.forEach { arr.put(it) }
            json.put("buttons", arr)
        }
        metadata?.let { json.put("metadata", it.toJSONObject()) }
        flags?.let { json.put("flags", it) }
        url?.let { json.put("url", it) }
        return json
    }
}

data class RichPresence(
    val activities: List<Activity> = emptyList(),
    val status: String = Constants.Status.ONLINE,
    val since: Long = System.currentTimeMillis(),
    val afk: Boolean = false,
) {
    fun toPayload(): JSONObject {
        val d = JSONObject()
        d.put("since", since)
        val actArr = JSONArray()
        activities.forEach { actArr.put(it.toJSONObject()) }
        d.put("activities", actArr)
        d.put("status", status)
        d.put("afk", afk)

        val packet = JSONObject()
        packet.put("op", Constants.Opcode.PRESENCE_UPDATE)
        packet.put("d", d)
        return packet
    }

    class Builder {
        private var applicationId: String? = null
        private var name: String = "Palleria"
        private var details: String? = null
        private var state: String? = null
        private var type: Int = Constants.ActivityType.PLAYING
        private var timestamps: Timestamps? = null
        private var assets: Assets? = null
        private var party: Party? = null
        private var secrets: Secrets? = null
        private var buttons: List<String>? = null
        private var metadata: Metadata? = null
        private var flags: Long? = ActivityFlags.INSTANCE
        private var url: String? = null
        private var status: String = Constants.Status.ONLINE
        private var since: Long = System.currentTimeMillis()
        private var afk: Boolean = false

        fun setApplicationId(appId: String?) = apply { this.applicationId = appId }

        fun setName(name: String) = apply { this.name = name }

        fun setDetails(details: String?) = apply { this.details = details }

        fun setState(state: String?) = apply { this.state = state }

        fun setType(type: Int) = apply { this.type = type }

        fun setTimestamps(timestamps: Timestamps?) = apply { this.timestamps = timestamps }

        fun setTimestamps(
            start: Long?,
            end: Long? = null,
        ) = apply { this.timestamps = Timestamps(start, end) }

        fun setAssets(assets: Assets?) = apply { this.assets = assets }

        fun setAssets(
            largeImage: String?,
            largeText: String? = null,
            smallImage: String? = null,
            smallText: String? = null,
        ) = apply { this.assets = Assets(largeImage, largeText, smallImage, smallText) }

        fun setParty(party: Party?) = apply { this.party = party }

        fun setSecrets(secrets: Secrets?) = apply { this.secrets = secrets }

        fun setButtons(buttons: List<String>?) = apply { this.buttons = buttons }

        fun setMetadata(metadata: Metadata?) = apply { this.metadata = metadata }

        fun setFlags(flags: Long?) = apply { this.flags = flags }

        fun setUrl(url: String?) = apply { this.url = url }

        fun setStatus(status: String) = apply { this.status = status }

        fun setSince(since: Long) = apply { this.since = since }

        fun setAfk(afk: Boolean) = apply { this.afk = afk }

        fun build(): RichPresence {
            val activity =
                Activity(
                    applicationId = applicationId,
                    name = name,
                    details = details,
                    state = state,
                    type = type,
                    timestamps = timestamps,
                    assets = assets,
                    party = party,
                    secrets = secrets,
                    buttons = buttons,
                    metadata = metadata,
                    flags = flags,
                    url = url,
                )
            return RichPresence(
                activities = listOf(activity),
                status = status,
                since = since,
                afk = afk,
            )
        }
    }
}

class CustomStatus(
    val text: String? = null,
    val emojiName: String? = null,
    val emojiId: String? = null,
    val isAnimated: Boolean = false,
) {
    fun toActivity(): Activity =
        Activity(
            name = "Custom Status",
            state = text,
            type = Constants.ActivityType.CUSTOM,
        )
}

class SpotifyRPC(
    val songTitle: String,
    val artist: String,
    val album: String? = null,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val trackId: String? = null,
    val albumCoverUrl: String? = null,
) {
    fun toActivity(): Activity =
        Activity(
            name = "Spotify",
            details = songTitle,
            state = artist,
            type = Constants.ActivityType.LISTENING,
            timestamps = Timestamps(start = startTime, end = endTime),
            assets =
                Assets(
                    largeImage = if (trackId != null) "spotify:$trackId" else albumCoverUrl,
                    largeText = album ?: songTitle,
                ),
            flags = 48L, // SYNC | PLAY
        )
}
