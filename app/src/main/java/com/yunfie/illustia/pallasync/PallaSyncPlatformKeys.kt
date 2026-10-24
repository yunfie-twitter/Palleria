package com.yunfie.illustia.pallasync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.StrongBoxUnavailableException
import androidx.security.crypto.MasterKey

/** §5.4: reuse existing wrappers; request StrongBox when generating a supported new key. */
internal object PallaSyncPlatformKeys {
    @Synchronized
    // StrongBox's documented unavailability permits the OS Keystore fallback; do not log endpoint details.
    @Suppress("SwallowedException")
    fun master(context: Context): MasterKey {
        fun build(strongBox: Boolean): MasterKey =
            MasterKey
                .Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(strongBox)
                .build()
        val request =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        return try {
            build(request)
        } catch (unavailable: StrongBoxUnavailableException) {
            build(false)
        }.also { check(it.isKeyStoreBacked) { "C_RECOVERY_STORAGE_UNSAFE" } }
    }
}
