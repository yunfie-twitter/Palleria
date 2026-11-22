package com.yunfie.illustia.platform

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ArtworkDownloadServiceTest {
    @Test
    fun keepsServiceUntilLastSaveAndReleasesEachLeaseOnlyOnce() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadow = shadowOf(app)
        val first = ArtworkDownloadService.acquire(app)
        val second = ArtworkDownloadService.acquire(app)
        try {
            assertEquals(ArtworkDownloadService::class.java.name, shadow.nextStartedService.component?.className)
            assertNull(shadow.nextStartedService)
            first.close()
            first.close()
            assertNull(shadow.nextStoppedService)
            second.close()
            assertNotNull(shadow.nextStoppedService)
            second.close()
            assertNull(shadow.nextStoppedService)
        } finally {
            first.close()
            second.close()
        }
        val next = ArtworkDownloadService.acquire(app)
        try {
            assertNotNull(shadow.nextStartedService)
        } finally {
            next.close()
        }
    }
}
