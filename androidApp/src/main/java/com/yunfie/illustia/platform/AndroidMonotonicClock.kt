package com.yunfie.illustia.platform

import android.os.Looper
import android.os.SystemClock

class AndroidMonotonicClock : MonotonicClock {
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}

class AndroidMainThreadChecker : MainThreadChecker {
    override fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
}
