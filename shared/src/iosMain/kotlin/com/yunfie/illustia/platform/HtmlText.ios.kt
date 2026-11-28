package com.yunfie.illustia.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSAttributedString
import platform.Foundation.NSDocumentTypeDocumentAttribute
import platform.Foundation.NSHTMLTextDocumentType
import platform.Foundation.NSMutableAttributedString
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding

/**
 * HTML 断片をプレーンテキストへ変換する。
 *
 * `android.text.Html` の代わりに Foundation の HTML インポートを使い、改行を正規化する。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun htmlToPlainText(html: String): String {
    val data =
        (html as NSString).dataUsingEncoding(NSUTF8StringEncoding)
            ?: return html.trim()
    val attributed =
        NSAttributedString
            .create(
                data = data,
                options = mapOf<Any?, Any?>(NSDocumentTypeDocumentAttribute to NSHTMLTextDocumentType),
                documentAttributes = null,
                error = null,
            ) ?: return html.trim()

    val plain = NSMutableAttributedString(attributed).string
    return plain
        .replace(NON_BREAKING_SPACE, ' ')
        .replace("\r\n", "\n")
        .trim()
}

private const val NON_BREAKING_SPACE = '\u00A0'
