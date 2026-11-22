package com.yunfie.illustia.models

import androidx.compose.runtime.Immutable

@Immutable
enum class Restrict(
    val apiValue: String,
) {
    Public("public"),
    Private("private"),
}
