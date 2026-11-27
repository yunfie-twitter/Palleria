package com.yunfie.illustia.platform

/**
 * HTML 断片をプレーンテキストへ変換する。
 *
 * HTML パーサはプラットフォーム依存のため expect/actual で分岐する。
 */
expect fun htmlToPlainText(html: String): String
