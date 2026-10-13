package com.yunfie.illustia.pallasync

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PallaSyncRecoveryOfflineMediaTest {
    @Test
    fun aDevicePackageCannotBeSavedAsOfflineBackupAndInvalidInputsWriteNoBytes() {
        val source =
            checkNotNull(javaClass.getResourceAsStream("/pallasync/python-recovery-c8-package.json"))
                .use { it.readBytes().toString(Charsets.UTF_8) }
        val admin =
            Json
                .parseToJsonElement(
                    source,
                ).jsonObject
                .getValue("manifest")
                .jsonObject
                .getValue("admin_public_key")
                .jsonPrimitive.content
        val output = ByteArrayOutputStream()
        shouldThrow<IllegalStateException> { PallaSyncRecoveryOfflineMedia.write(output, source, admin, null) }
            .message shouldBe "C_RECOVERY_STORAGE_UNSAFE"
        output.size() shouldBe 0
        shouldThrow<IllegalStateException> { PallaSyncRecoveryOfflineMedia.write(output, "{}", admin, null) }
            .message shouldBe "C_RECOVERY_SHARE_INVALID"
        output.size() shouldBe 0
    }

    @Test
    fun hostedProvidersAndHttpsCannotReceiveOrSupplyRecoveryMedia() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("https://example.org/recovery", "content://com.example.cloud.documents/package", "file:///tmp/recovery").forEach { path ->
            shouldThrow<IllegalStateException> {
                PallaSyncRecoveryOfflineMedia.write(context, Uri.parse(path), "{}", "", null)
            }.message shouldBe "C_RECOVERY_STORAGE_UNSAFE"
            shouldThrow<IllegalStateException> {
                PallaSyncRecoveryOfflineMedia.read(context, Uri.parse(path), "", null)
            }.message shouldBe "C_RECOVERY_STORAGE_UNSAFE"
        }
    }
}
