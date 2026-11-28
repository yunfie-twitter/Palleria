package com.yunfie.illustia.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSAttributedString
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding

/**
 * HTML 断片をプレーンテキストへ変換する。
 *
 * `android.text.Html` の代わりに Foundation の HTML 読み込みを使う。
 * `NSDocumentTypeDocumentAttribute` / `NSHTMLTextDocumentType` は Kotlin/Native の
 * Foundation バインディングには定数として公開されていないので、実際のキー文字列と
 * 値を自前で定義する（値は Apple の公開ドキュメントどおり）。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun htmlToPlainText(html: String): String {
    val data = (html as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return html.trim()
    val typed: NSAttributedString? =
        NSAttributedString.create(
            data = data,
            options = mapOf<Any?, Any?>(DOCUMENT_TYPE_ATTRIBUTE to HTML_DOCUMENT_TYPE),
            documentAttributes = null,
            error = null,
        )
    val plain = typed?.string ?: return html.trim()
    return plain
        .replace(NON_BREAKING_SPACE, ' ')
        .replace("\r\n", "\n")
        .trim()
}

private const val DOCUMENT_TYPE_ATTRIBUTE = "NSDocumentTypeDocumentAttribute"
private const val HTML_DOCUMENT_TYPE = "NSHTMLTextDocumentType"
private const val NON_BREAKING_SPACE = '\u00A0'
