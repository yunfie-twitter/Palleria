package com.yunfie.illustia.nativebridge

/**
 * `%XX` 形式をデコードする最小実装。
 *
 * `java.net.URLDecoder` は `+` を空白に変換してしまい、Pixiv のタグでは
 * 意図しない置換になる。ここではパーセントエンコードだけを解く。
 */
internal fun decodeUrlComponent(value: String): String {
    if (!value.contains('%')) return value
    val bytes = ArrayList<Byte>(value.length)
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val hex = value.substring(i + 1, i + 3)
            val parsed = hex.toIntOrNull(16)
            if (parsed != null) {
                bytes.add(parsed.toByte())
                i += 3
                continue
            }
        }
        val encoded = c.toString().encodeToByteArray()
        encoded.forEach { bytes.add(it) }
        i++
    }
    return bytes.toByteArray().decodeToString()
}
