package com.yunfie.illustia.ui.app

import android.content.Context
import android.content.Intent

internal fun startTextShare(
    context: Context,
    text: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    context.startActivity(Intent.createChooser(intent, null))
}
