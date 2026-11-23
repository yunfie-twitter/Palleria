package com.yunfie.illustia.platform

import android.content.Context
import java.io.File

class AndroidAppPaths(
    private val context: Context,
) : AppPaths {
    override fun cacheDir(): File = context.cacheDir
}
