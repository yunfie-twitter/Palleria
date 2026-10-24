package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PallaSyncProbeScheduleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferencesName = "probe-schedule-${UUID.randomUUID()}"
    private val preferences by lazy { context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE) }

    @After
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun intervalAndSuccessfulComparisonArePersistedPerChain() {
        val schedule = PallaSyncProbeSchedule(preferences)
        val chain = "chain-${UUID.randomUUID()}"
        val otherChain = "chain-${UUID.randomUUID()}"

        assertEquals(PallaSyncProbeSchedule.DEFAULT_INTERVAL_MS, schedule.intervalMs(chain))
        assertNull(schedule.lastSuccessAtMs(chain))

        schedule.setIntervalMs(chain, 60 * 60_000L)
        schedule.recordSuccess(chain, 1_800_000_000_000L)

        val reopened = PallaSyncProbeSchedule(preferences)
        assertEquals(60 * 60_000L, reopened.intervalMs(chain))
        assertEquals(1_800_000_000_000L, reopened.lastSuccessAtMs(chain))
        assertEquals(PallaSyncProbeSchedule.DEFAULT_INTERVAL_MS, reopened.intervalMs(otherChain))
        assertNull(reopened.lastSuccessAtMs(otherChain))
    }

    @Test
    fun intervalCannotBeShorterThanTheProtocolDefault() {
        val schedule = PallaSyncProbeSchedule(preferences)
        assertThrows(IllegalArgumentException::class.java) {
            schedule.setIntervalMs("chain", PallaSyncProbeSchedule.DEFAULT_INTERVAL_MS - 1)
        }
        assertFalse(preferences.contains("consistency-probe-interval:chain"))
    }
}
