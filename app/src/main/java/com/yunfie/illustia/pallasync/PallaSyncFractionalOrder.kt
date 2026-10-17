package com.yunfie.illustia.pallasync

import java.math.BigInteger

/** §15.5 exact rational allocation. Virtual endpoints are null; no floating point or implicit reindex. */
internal object PallaSyncFractionalOrder {
    private const val MAX_ORDER_BYTES = 256
    private const val RADIX = 16
    private const val DIGIT_BITS = 4

    private data class Fraction(
        val integer: BigInteger,
        val digits: Int,
    )

    private fun parse(key: String): Fraction {
        require(
            key.length in 1..MAX_ORDER_BYTES && key.all { it in '0'..'9' || it in 'a'..'f' } && key.last() != '0',
        ) { "C_SCHEMA_VIOLATION" }
        return Fraction(BigInteger(key, RADIX), key.length)
    }

    fun between(
        lower: String?,
        upper: String?,
    ): String {
        val a = lower?.let(::parse) ?: Fraction(BigInteger.ZERO, 0)
        val b = upper?.let(::parse) ?: Fraction(BigInteger.ONE, 0)
        require(a.integer.shiftLeft(DIGIT_BITS * b.digits) < b.integer.shiftLeft(DIGIT_BITS * a.digits)) { "C_SCHEMA_VIOLATION" }
        for (digits in 1..MAX_ORDER_BYTES) {
            val scale = DIGIT_BITS * (digits - a.digits)
            val floor = if (scale >= 0) a.integer.shiftLeft(scale) else a.integer.shiftRight(-scale)
            val candidate = floor + BigInteger.ONE
            if (candidate.shiftLeft(DIGIT_BITS * b.digits) < b.integer.shiftLeft(DIGIT_BITS * digits)) {
                return candidate.toString(RADIX).padStart(digits, '0').trimEnd('0')
            }
        }
        error("C_ORDER_KEY_EXHAUSTED")
    }

    /** Allocate changed runs between their retained neighboring keys. Creation has no retained keys. */
    fun place(
        order: List<String>,
        changed: Set<String>,
        retained: Map<String, String>,
        appendAfter: String? = null,
    ): Map<String, String> {
        require(order.distinct().size == order.size && order.containsAll(changed)) { "C_SCHEMA_VIOLATION" }
        val result = linkedMapOf<String, String>()
        var position = 0
        while (position < order.size) {
            if (order[position] !in changed) {
                result[order[position]] = retained[order[position]] ?: error("C_MISSING_DEPENDENCY")
                position++
                continue
            }
            val start = position
            while (position < order.size && order[position] in changed) position++
            val visibleLower = if (start == 0) null else result[order[start - 1]]
            val lower = if (position == order.size) listOfNotNull(visibleLower, appendAfter).maxOrNull() else visibleLower
            val upper = if (position == order.size) null else retained[order[position]] ?: error("C_MISSING_DEPENDENCY")

            fun fill(
                first: Int,
                end: Int,
                a: String?,
                b: String?,
            ) {
                if (first >= end) return
                val middle = first + (end - first) / 2
                val key = between(a, b)
                result[order[middle]] = key
                fill(first, middle, a, key)
                fill(middle + 1, end, key, b)
            }
            fill(start, position, lower, upper)
        }
        check(order.zipWithNext().all { (a, b) -> result.getValue(a) < result.getValue(b) }) { "C_MISSING_DEPENDENCY" }
        return result
    }
}
