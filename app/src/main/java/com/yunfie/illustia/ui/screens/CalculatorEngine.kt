package com.yunfie.illustia.ui.screens

/**
 * 電卓の計算ロジックを提供する純粋関数オブジェクト。
 * ViewModel の状態から独立してテスト可能な設計にする。
 */
object CalculatorEngine {

    /**
     * 数式文字列を評価し、結果を Double または null（エラー）で返す。
     * ゼロ除算・構文エラーは null を返す。例外を UI 層に伝播させない。
     */
    fun evaluate(expression: String): Double? {
        if (expression.isBlank()) return null
        return try {
            // × と ÷ を標準記号に正規化
            val normalized = expression
                .replace('×', '*')
                .replace('÷', '/')
                .trim()
            val tokens = tokenize(normalized)
            if (tokens.isEmpty()) return null
            val pos = intArrayOf(0)
            val result = parseAddSub(tokens, pos)
            if (pos[0] != tokens.size) return null // 余分なトークンがある
            if (result.isNaN() || result.isInfinite()) null else result
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 結果をユーザー表示用文字列にフォーマットする。
     * 整数結果（例: 2.0）は小数点以下を省略して "2" として返す。
     * NaN や Infinity は "エラー" を返す。
     */
    fun formatResult(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "エラー"
        return if (value == kotlin.math.floor(value) && kotlin.math.abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            // 余分な末尾 0 を除去（最大 10 桁）
            "%.10f".format(value).trimEnd('0').trimEnd('.')
        }
    }

    // ── 字句解析 ──────────────────────────────────────────────────────────────

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val ch = expr[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    tokens.add(expr.substring(start, i))
                }
                ch in "+-*/" -> { tokens.add(ch.toString()); i++ }
                else -> throw ArithmeticException("Unknown character: $ch")
            }
        }
        return tokens
    }

    // ── 再帰降下パーサー ───────────────────────────────────────────────────────

    private fun parseAddSub(tokens: List<String>, pos: IntArray): Double {
        var left = parseMulDiv(tokens, pos)
        while (pos[0] < tokens.size && tokens[pos[0]] in listOf("+", "-")) {
            val op = tokens[pos[0]++]
            val right = parseMulDiv(tokens, pos)
            left = if (op == "+") left + right else left - right
        }
        return left
    }

    private fun parseMulDiv(tokens: List<String>, pos: IntArray): Double {
        var left = parseUnary(tokens, pos)
        while (pos[0] < tokens.size && tokens[pos[0]] in listOf("*", "/")) {
            val op = tokens[pos[0]++]
            val right = parseUnary(tokens, pos)
            left = when {
                op == "/" && right == 0.0 -> return Double.NaN
                op == "*" -> left * right
                else -> left / right
            }
        }
        return left
    }

    private fun parseUnary(tokens: List<String>, pos: IntArray): Double {
        if (pos[0] < tokens.size && tokens[pos[0]] == "-") {
            pos[0]++
            return -parseUnary(tokens, pos)
        }
        if (pos[0] < tokens.size && tokens[pos[0]] == "+") {
            pos[0]++
            return parseUnary(tokens, pos)
        }
        return parsePrimary(tokens, pos)
    }

    private fun parsePrimary(tokens: List<String>, pos: IntArray): Double {
        if (pos[0] >= tokens.size) throw ArithmeticException("Unexpected end of expression")
        val token = tokens[pos[0]++]
        return token.toDoubleOrNull() ?: throw ArithmeticException("Not a number: $token")
    }
}
