package com.yunfie.illustia.platform

/**
 * `%1$s` / `%1$d` / `%%` の差し込み。
 *
 * Compose Multiplatform の `getString` は `suspend` のため、ViewModel の同期 `str()`
 * から直接は呼べない。文言をキャッシュし、書式適用だけをこの純粋関数で行う。
 *
 * `StringKey` が実際に使う書式はこの 3 種のみ（既定ロケールで確認済み）。
 * 引数が足りない場合は書式をそのまま残し、表示から欠落に気付けるようにする。
 */
fun formatStringArgs(
    template: String,
    args: Array<out Any>,
): String =
    buildString {
        var i = 0
        while (i < template.length) {
            if (template[i] == '%' && i + 1 < template.length) {
                when {
                    template[i + 1] == '%' -> {
                        append('%')
                        i += 2
                        continue
                    }

                    template[i + 1].isDigit() -> {
                        var j = i + 1
                        while (j < template.length && template[j].isDigit()) j++
                        val position = template.substring(i + 1, j).toInt() - 1
                        if (j + 1 < template.length && template[j] == '$') {
                            val type = template[j + 1]
                            if (type == 's' || type == 'd') {
                                val value = args.getOrNull(position)
                                if (value != null) {
                                    append(value.toString())
                                    i = j + 2
                                    continue
                                }
                            }
                        }
                    }
                }
            }
            append(template[i])
            i++
        }
    }
