package com.yunfie.illustia.platform

import android.util.Log

class AndroidAppLogger : AppLogger {
    override fun warn(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (throwable != null) {
            Log.w(tag, message, throwable)
        } else {
            Log.w(tag, message)
        }
    }
}
