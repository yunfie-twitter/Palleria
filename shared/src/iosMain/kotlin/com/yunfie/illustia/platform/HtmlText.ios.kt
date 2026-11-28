package com.yunfie.illustia.platform

/**
 * HTML 断片をプレーンテキストへ変換する。
 *
 * Foundation の `NSAttributedString` による HTML 読み込みは Kotlin/Native のバインディングで
 * 初期化子の形が安定せず、commonTest からも叩きにくい。Pixiv のコメントはタグと実体参照
 * だけの素朴な HTML なので、依存の無いタグ除去 + エンティティ展開で十分。
 */
actual fun htmlToPlainText(html: String): String = stripHtmlTags(html)
