package com.yunfie.illustia

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelemetryStartupManifestTest {
    @Test
    fun `merged manifest does not install Sentry startup providers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        @Suppress("DEPRECATION")
        val providers =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS)
                .providers
                .orEmpty()
        providers.any { it.name.startsWith("io.sentry.") } shouldBe false
    }
}
