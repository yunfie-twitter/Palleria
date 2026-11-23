package com.yunfie.illustia.platform

import android.content.Context

class AndroidDeviceProfile(
    context: Context,
) : DeviceProfile {
    private val appContext = context.applicationContext

    override val isLowRamDevice: Boolean
        get() = PlatformCapabilities.isLowRamDevice(appContext)

    override val dataStoreDebounceMs: Long
        get() = PlatformCapabilities.recommendedDataStoreDebounceMs(appContext)
}
