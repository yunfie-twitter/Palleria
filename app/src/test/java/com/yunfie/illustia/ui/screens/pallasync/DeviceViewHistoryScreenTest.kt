package com.yunfie.illustia.ui.screens.pallasync

import com.yunfie.illustia.R
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceViewHistoryScreenTest {
    @Test
    fun incompleteHistoryUsesThePartialSummary() {
        assertEquals(R.string.pallasync_v3_history_summary_partial, historySummaryResource(partial = true, statusPartial = false))
        assertEquals(R.string.pallasync_v3_history_summary_partial, historySummaryResource(partial = false, statusPartial = true))
        assertEquals(R.string.pallasync_v3_history_summary_complete, historySummaryResource(partial = false, statusPartial = false))
    }
}
