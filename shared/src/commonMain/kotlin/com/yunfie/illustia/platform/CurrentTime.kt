package com.yunfie.illustia.platform

import kotlin.time.Clock

/** `System.currentTimeMillis()` の multiplatform 代替。 */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
