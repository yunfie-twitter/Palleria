package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable

@Immutable
enum class NetworkMode(val code: String) {
    Compat("compat"),
    Ech("ech"),
    Standard("standard");

    companion object {
        fun fromCode(code: String?): NetworkMode {
            if (code == Compat.code) return Ech
            for (mode in entries) {
                if (mode.code == code) return mode
            }
            return Standard
        }
    }

    val usesCompatibleConnection: Boolean get() = this != Standard
    val allowsImageSource: Boolean get() = this != Standard
}
