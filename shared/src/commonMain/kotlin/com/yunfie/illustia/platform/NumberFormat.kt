package com.yunfie.illustia.platform

/**
 * `String.format` の代替。commonMain には `java.util.Formatter` が無いので、
 * このリポジトリで実際に使っている書式だけを手書きでカバーする。
 *
 * 対応:
 * - `%.Nf`  小数 N 桁固定（四捨五入）
 * - `%06X`  0 詰め 6 桁の大文字 16 進
 */
fun formatDecimal(
    value: Double,
    decimals: Int,
): String {
    require(decimals >= 0) { "decimals must not be negative" }
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val rounded = kotlin.math.round(kotlin.math.abs(value) * factor).toLong()
    val whole = rounded / factor
    val fraction = rounded % factor
    val sign = if (value < 0 && rounded != 0L) "-" else ""
    if (decimals == 0) return "$sign$whole"
    val fractionText = fraction.toString().padStart(decimals, '0')
    return "$sign$whole.$fractionText"
}

/** `String.format("%06X", value)` と同じ（0 詰め 6 桁の大文字 16 進）。 */
fun formatHex6(value: Int): String {
    val hex = (value and 0xFFFFFF).toString(16).uppercase()
    return hex.padStart(6, '0')
}
