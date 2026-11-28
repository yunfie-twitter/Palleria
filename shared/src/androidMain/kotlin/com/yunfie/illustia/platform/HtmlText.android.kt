package com.yunfie.illustia.platform

import android.text.Html

actual fun htmlToPlainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
