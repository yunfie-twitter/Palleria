package com.yunfie.illustia.settings

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.models.PixivSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class PersistedSessionTest {
    private val preferences get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("session-test", 0)

    @Test fun restoresOnlyUnexpiredMatchingCredentials() {
        val session = PixivSession("access", "account-one", 42, 3_601_000)
        preferences.edit().putString(KEY_CACHED_SESSION, encodePersistedSession(session)).commit()
        assertEquals(session, readPersistedSession(preferences, "account-one", 1000))
        assertNull(readPersistedSession(preferences, "account-two", 1000))
        assertNull(readPersistedSession(preferences, "", 1000))
        assertNull(readPersistedSession(preferences, "account-one", 3_541_000))
    }

    @Test fun corruptMissingAndImplausibleExpiryFallBackToOAuth() {
        preferences.edit().putString(KEY_CACHED_SESSION, "broken").commit()
        assertNull(readPersistedSession(preferences, "refresh", 1000))
        listOf(0L, Long.MAX_VALUE).forEach { expiry ->
            preferences
                .edit()
                .putString(
                    KEY_CACHED_SESSION,
                    encodePersistedSession(PixivSession("access", "refresh", null, expiry)),
                ).commit()
            assertNull(readPersistedSession(preferences, "refresh", 1000))
        }
    }
}
