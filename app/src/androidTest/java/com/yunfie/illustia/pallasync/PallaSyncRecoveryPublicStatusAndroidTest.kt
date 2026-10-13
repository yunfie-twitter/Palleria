package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real Android JNI, protected public prefs and local file readback; no physical recovery-capacity claim. */
@RunWith(AndroidJUnit4::class)
class PallaSyncRecoveryPublicStatusAndroidTest {
    @Test
    fun interruptedPreparationCanBeRetiredWithFreshRootAndOriginalStatusesSurviveFileReadback() {
        ActivityScenario.launch(PallaSyncRecoveryTestActivity::class.java).use {
            PallaSyncCore.setSecretForeground(true)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    "pallasync-public-status-native-test",
                    PallaSyncPlatformKeys.master(context),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            assertTrue("Test-owned public preferences must be empty", prefs.all.isEmpty())
            val path = File(context.cacheDir, "pallasync-public-status-native-test.json")
            assertEquals(context.cacheDir.canonicalFile, path.canonicalFile.parentFile)
            assertFalse("Test-owned output must not overwrite another file", path.exists())
            try {
                val store = PallaSyncRecoverySetStore(prefs)
                val entry = prepare(store)
                val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
                val retired =
                    PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_DISABLE).use { root ->
                        signRecoveryRetirement(root, store, entry)
                    }
                assertEquals("retired", retired.state)
                path.outputStream().use { output -> PallaSyncRecoveryPublicStatusMedia.write(output, retired) }
                val verified = path.inputStream().use { input -> PallaSyncRecoveryPublicStatusMedia.read(input, entry) }
                assertEquals(retired.statuses, Json.parseToJsonElement(verified))
                assertEquals(retired, PallaSyncRecoverySetStore(prefs).read(entry.id))
                assertFalse(verified.contains("encrypted_blob"))
                assertFalse(verified.contains("encrypted_share"))
                assertFalse(retired.ordinaryRecoveryAvailable)
            } finally {
                assertTrue(prefs.edit().clear().commit())
                if (path.exists()) assertTrue(path.delete())
                PallaSyncCore.setSecretForeground(false)
            }
        }
    }

    private fun prepare(store: PallaSyncRecoverySetStore): PallaSyncRecoverySetStore.Entry {
        val phrase = (List(23) { "abandon" } + "art").joinToString(" ")
        return PallaSyncRootSession.beginText(phrase, "", PallaSyncRootSession.Operation.RECOVERY_CREATE).use { root ->
            val holders =
                JsonArray(
                    List(3) {
                        buildJsonObject {
                            put("holder_kind", "offline")
                            put("holder_device_id", JsonNull)
                            put("holder_instance_id", UUID.randomUUID().toString())
                        }
                    },
                )
            val prepared =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.prepareRecoverySet(
                            root.handle,
                            root.operation.wire,
                            0,
                            2,
                            holders.toString(),
                            System.currentTimeMillis(),
                        ),
                    ).jsonObject
            val entry =
                store.create(
                    prepared.getValue("manifest").toString(),
                    prepared.getValue("status_chain").toString(),
                    root.identity.adminPublicKey,
                )
            store.interrupted(entry)
        }
    }
}
