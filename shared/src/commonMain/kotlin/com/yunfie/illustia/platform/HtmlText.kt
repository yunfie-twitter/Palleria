package com.yunfie.illustia.platform

/**
 * HTML 断片をプレーンテキストへ変換する。
 *
 * HTML パーサはプラットフォーム依存のため expect/actual で分岐する。
 */
expect fun htmlToPlainText(html: String): String

/**
 * タグ除去 + 実体参照の展開で HTML をプレーンテキスト化する。
 *
 * iOS のように専用 HTML パーサへ依存したくないプラットフォーム向けの実装で、
 * 同じ挙動を commonTest から検証できるようここに置く。
 */
fun stripHtmlTags(html: String): String {
    val withoutTags = TAG_PATTERN.replace(html, " ")
    val decoded =
        ENTITY_PATTERN.replace(withoutTags) { match ->
            when (val name = match.groupValues[1].lowercase()) {
                "nbsp" -> " "
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos", "#39" -> "'"
                else -> decodeNumericEntity(name) ?: match.value
            }
        }
    return decoded
        .replace(BREAKING_WHITESPACE, ' ')
        .lines()
        .joinToString("\n") { line -> SPACE_RUN.replace(line, " ").trim() }
        .trim()
}

private val TAG_PATTERN = Regex("""<[^>]*>""")
private val ENTITY_PATTERN = Regex("""&(#?[A-Za-z0-9]+);""")
private val SPACE_RUN = Regex("""[ \t]+""")
private const val BREAKING_WHITESPACE = '\u00A0'

/** `&#123;` / `&#x7B;` を展開する。範囲外や不正値は null。 */
private fun decodeNumericEntity(name: String): String? {
    if (!name.startsWith("#")) return null
    val body = name.removePrefix("#")
    val codePoint =
        if (body.startsWith("x") || body.startsWith("X")) {
            body.drop(1).toIntOrNull(16)
        } else {
            body.toIntOrNull()
        } ?: return null
    if (codePoint !in 1..0x10FFFF) return null
    if (codePoint <= 0xFFFF) return codePoint.toChar().toString()
    val offset = codePoint - 0x10000
    val high = (0xD800 + (offset shr 10)).toChar()
    val low = (0xDC00 + (offset and 0x3FF)).toChar()
    return "$high$low"
}
