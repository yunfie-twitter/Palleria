package com.yunfie.illustia.platform

import android.content.Context

class AndroidAppPaths(
    private val context: Context,
) : AppPaths {
    override fun cacheDir(): String = context.cacheDir.absolutePath
}
