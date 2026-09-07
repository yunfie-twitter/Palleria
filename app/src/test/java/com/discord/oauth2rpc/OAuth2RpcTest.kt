package com.discord.oauth2rpc

import com.discord.oauth2rpc.structures.Assets
import com.discord.oauth2rpc.structures.CustomStatus
import com.discord.oauth2rpc.structures.Metadata
import com.discord.oauth2rpc.structures.RichPresence
import com.discord.oauth2rpc.structures.SpotifyRPC
import com.discord.oauth2rpc.structures.Timestamps
import com.discord.oauth2rpc.utils.ActivityFlags
import com.discord.oauth2rpc.utils.BitField
import com.discord.oauth2rpc.utils.Constants
import com.discord.oauth2rpc.utils.GatewayCapabilities
import com.discord.oauth2rpc.utils.Intents
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OAuth2RpcTest {
    @Test
    fun testBitFieldOperations() {
        val bitField = BitField()
        bitField.has(1L shl 2) shouldBe false

        bitField.add(1L shl 2, 1L shl 4)
        bitField.has(1L shl 2) shouldBe true
        bitField.has(1L shl 4) shouldBe true
        bitField.has(1L shl 1) shouldBe false

        bitField.remove(1L shl 2)
        bitField.has(1L shl 2) shouldBe false
        bitField.has(1L shl 4) shouldBe true

        val missing = bitField.missing(1L shl 2, 1L shl 4, 1L shl 5)
        missing shouldBe listOf(1L shl 2, 1L shl 5)
    }

    @Test
    fun testIntentsAndCapabilities() {
        val intents = Intents()
        intents.add(Intents.GUILDS, Intents.GUILD_MESSAGES)
        intents.has(Intents.GUILDS) shouldBe true
        intents.has(Intents.DIRECT_MESSAGES) shouldBe false

        val caps = GatewayCapabilities()
        caps.add(GatewayCapabilities.LAZY_USER_NOTES, GatewayCapabilities.CLIENT_TRACK_STATUS)
        caps.has(GatewayCapabilities.LAZY_USER_NOTES) shouldBe true

        val flags = ActivityFlags()
        flags.add(ActivityFlags.INSTANCE, ActivityFlags.SYNC, ActivityFlags.PLAY)
        flags.has(ActivityFlags.SYNC) shouldBe true
    }

    @Test
    fun testRichPresencePayload() {
        val presence =
            RichPresence
                .Builder()
                .setApplicationId("1234567890")
                .setName("Palleria")
                .setDetails("Viewing artwork")
                .setState("by Artist")
                .setType(Constants.ActivityType.PLAYING)
                .setTimestamps(1000L, 2000L)
                .setAssets("palleria_logo", "Palleria Logo", "small_icon", "Small Text")
                .setButtons(listOf("Pixivで見る", "Palleriaをダウンロード"))
                .setMetadata(Metadata(listOf("https://pixiv.net/artworks/123", "https://yunfi.f5.si/Palleria/user/installation")))
                .setStatus(Constants.Status.ONLINE)
                .setAfk(false)
                .build()

        val payload = presence.toPayload()
        payload.getInt("op") shouldBe Constants.Opcode.PRESENCE_UPDATE

        val d = payload.getJSONObject("d")
        d.getString("status") shouldBe "online"
        d.getBoolean("afk") shouldBe false

        val activities = d.getJSONArray("activities")
        activities.length() shouldBe 1

        val activity = activities.getJSONObject(0)
        activity.getString("application_id") shouldBe "1234567890"
        activity.getString("name") shouldBe "Palleria"
        activity.getString("details") shouldBe "Viewing artwork"
        activity.getString("state") shouldBe "by Artist"
        activity.getInt("type") shouldBe 0

        val timestamps = activity.getJSONObject("timestamps")
        timestamps.getLong("start") shouldBe 1000L
        timestamps.getLong("end") shouldBe 2000L

        val assets = activity.getJSONObject("assets")
        assets.getString("large_image") shouldBe "palleria_logo"
        assets.getString("large_text") shouldBe "Palleria Logo"

        val buttons = activity.getJSONArray("buttons")
        buttons.length() shouldBe 2
        buttons.getString(0) shouldBe "Pixivで見る"
        buttons.getString(1) shouldBe "Palleriaをダウンロード"

        val metadata = activity.getJSONObject("metadata")
        val buttonUrls = metadata.getJSONArray("button_urls")
        buttonUrls.length() shouldBe 2
        buttonUrls.getString(0) shouldBe "https://pixiv.net/artworks/123"
        buttonUrls.getString(1) shouldBe "https://yunfi.f5.si/Palleria/user/installation"
    }

    @Test
    fun testCustomStatusAndSpotify() {
        val custom = CustomStatus(text = "Exploring Palleria", emojiName = "art")
        val customActivity = custom.toActivity()
        customActivity.state shouldBe "Exploring Palleria"
        customActivity.type shouldBe Constants.ActivityType.CUSTOM

        val spotify =
            SpotifyRPC(
                songTitle = "Track Title",
                artist = "Artist Name",
                album = "Album Name",
                startTime = 5000L,
                endTime = 8000L,
                trackId = "abc123",
            )
        val spotifyActivity = spotify.toActivity()
        spotifyActivity.name shouldBe "Spotify"
        spotifyActivity.details shouldBe "Track Title"
        spotifyActivity.state shouldBe "Artist Name"
        spotifyActivity.type shouldBe Constants.ActivityType.LISTENING
        spotifyActivity.assets?.largeImage shouldBe "spotify:abc123"
        spotifyActivity.assets?.largeText shouldBe "Album Name"
    }

    @Test
    fun testRestRoutes() {
        Rest.Route.CURRENT_USER.path shouldBe "/users/@me"
        Rest.Route.CURRENT_USER.method shouldBe Rest.Method.GET

        Rest.Route.OAUTH2_TOKEN.path shouldBe "/oauth2/token"
        Rest.Route.OAUTH2_TOKEN.method shouldBe Rest.Method.POST

        val customRoute = Rest.Route.get("/guilds/123")
        customRoute.path shouldBe "/guilds/123"
        customRoute.method shouldBe Rest.Method.GET
    }

    @Test
    fun testGatewayPacketAndReadyEvent() {
        val user =
            User(
                id = "987654321",
                username = "TestUser",
                discriminator = "0001",
                globalName = "Global Name",
                avatar = "avatar_hash",
                bot = false,
            )
        val ready =
            ReadyEvent(
                version = 10,
                user = user,
                sessionType = "normal",
                sessionId = "sess_123",
                resumeGatewayUrl = "wss://resume.discord.gg",
            )
        ready.version shouldBe 10
        ready.user.username shouldBe "TestUser"
        ready.sessionId shouldBe "sess_123"

        val packet = GatewayPacket(op = Constants.Opcode.HEARTBEAT, d = 42, s = 1)
        val json = packet.toJSONObject()
        json.getInt("op") shouldBe Constants.Opcode.HEARTBEAT
        json.getInt("d") shouldBe 42
        json.getInt("s") shouldBe 1
    }
}
