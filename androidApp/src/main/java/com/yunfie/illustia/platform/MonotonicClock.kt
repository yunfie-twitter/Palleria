package com.yunfie.illustia.platform

/** ViewModel 層が使う時刻とスレッド判定の境界。 */
interface MonotonicClock {
    /** 端末起動からの経過ミリ秒。単調増加であることだけを保証する。 */
    fun elapsedRealtimeMillis(): Long
}

interface MainThreadChecker {
    fun isMainThread(): Boolean
}
