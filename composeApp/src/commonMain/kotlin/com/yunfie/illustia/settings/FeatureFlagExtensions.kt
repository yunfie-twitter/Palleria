package com.yunfie.illustia.settings

fun AppSettings.isFeatureEnabled(flag: FeatureFlag): Boolean = flag.resolve(this)
