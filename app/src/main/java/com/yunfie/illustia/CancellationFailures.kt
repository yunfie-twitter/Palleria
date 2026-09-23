package com.yunfie.illustia

import java.util.concurrent.CancellationException

fun Throwable.isCancellationFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is CancellationException) return true
        current = current.cause
    }
    return false
}
