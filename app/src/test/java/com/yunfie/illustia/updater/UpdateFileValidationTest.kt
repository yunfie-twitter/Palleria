package com.yunfie.illustia.updater

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldStartWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UpdateFileValidationTest {
    @Test
    fun rejectsSiblingDirectoriesWithTheUpdatesPrefix() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppUpdaterRepository(context)
        val siblingFile = File(context.cacheDir, "updates-untrusted/update.apk")

        shouldThrow<IllegalArgumentException> {
            repository.validateApkFile(siblingFile)
        }.message.orEmpty().shouldStartWith("Unauthorized APK file location:")
    }

    @Test
    fun rejectsTraversalOutsideTheUpdatesDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppUpdaterRepository(context)
        val escapedFile = File(context.cacheDir, "updates/../updates-untrusted/update.apk")

        shouldThrow<IllegalArgumentException> {
            repository.validateApkFile(escapedFile)
        }.message.orEmpty().shouldStartWith("Unauthorized APK file location:")
    }

    @Test
    fun allowsPathsInsideTheUpdatesDirectoryToReachFileValidation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AppUpdaterRepository(context)
        val updateFile = File(context.cacheDir, "updates/nonexistent-update.apk")

        shouldThrow<IllegalArgumentException> {
            repository.validateApkFile(updateFile)
        }.message.orEmpty().shouldStartWith("Valid APK file required:")
    }
}
