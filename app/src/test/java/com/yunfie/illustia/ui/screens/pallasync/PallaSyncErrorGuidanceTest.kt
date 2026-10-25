package com.yunfie.illustia.ui.screens.pallasync

import android.app.Application
import com.yunfie.illustia.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PallaSyncErrorGuidanceTest {
    /**
     * These codes report an integrity or policy failure without establishing a single cause, so a
     * specific hint would claim more than the client can prove. They must stay neutral.
     */
    @Test
    fun `integrity and policy codes fall back to the neutral hint`() {
        listOf(
            "C_DEVICE_EQUIVOCATION",
            "C_ROLLBACK_DETECTED",
            "C_LOG_HASH_MISMATCH",
            "C_TRANSPARENCY_INVALID",
            "C_DEVICE_REVOKED",
            "C_RECORD_ID_CONFLICT",
            "C_RECOVERY_AUTH_REQUIRED",
            "C_RECOVERY_SHARE_COMPROMISED",
            "C_WRITE_PAUSED",
            "C_DELETION_UNVERIFIED",
        ).forEach { code ->
            assertEquals(
                "$code must not select a hint that asserts an unverified cause",
                R.string.pallasync_error_hint_generic,
                pallaSyncErrorGuidanceRes(code),
            )
        }
    }

    /** Codes whose cause the hint actually describes keep their mapping. */
    @Test
    fun `codes with a matching cause keep their specific hint`() {
        assertEquals(R.string.pallasync_error_hint_trust, pallaSyncErrorGuidanceRes("C_BOOTSTRAP_TRUST_REQUIRED"))
        assertEquals(R.string.pallasync_error_hint_signature, pallaSyncErrorGuidanceRes("C_SIGNATURE_INVALID"))
        assertEquals(R.string.pallasync_error_hint_schema, pallaSyncErrorGuidanceRes("C_SCHEMA_VIOLATION"))
        assertEquals(R.string.pallasync_error_hint_storage, pallaSyncErrorGuidanceRes("C_LOCAL_STORAGE_FULL"))
        assertEquals(R.string.pallasync_error_hint_network, pallaSyncErrorGuidanceRes("C_RELAY_UNAVAILABLE"))
        assertEquals(R.string.pallasync_error_hint_missing_file, pallaSyncErrorGuidanceRes("C_MISSING_DEPENDENCY"))
    }

    @Test
    fun `unrecognised and blank codes use the neutral hint`() {
        assertEquals(R.string.pallasync_error_hint_generic, pallaSyncErrorGuidanceRes(null))
        assertEquals(R.string.pallasync_error_hint_generic, pallaSyncErrorGuidanceRes("  "))
        assertEquals(R.string.pallasync_error_hint_generic, pallaSyncErrorGuidanceRes("C_NOT_A_REAL_CODE"))
    }
}
