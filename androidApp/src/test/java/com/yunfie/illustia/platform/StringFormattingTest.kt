package com.yunfie.illustia.platform

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * `StringProvider` を Compose Resources に移すにあたり、Android の `getString` と
 * 同じ書式結果になることを確認する。`StringKey` が実際に使う書式は
 * `%1$s` / `%1$d` / `%%` の 3 種のみ（既定ロケールで確認済み）。
 */
class StringFormattingTest :
    StringSpec({
        "substitutes a single string argument" {
            formatStringArgs("#%1\$s をWatchlistに追加しました。", arrayOf("tag")) shouldBe
                "#tag をWatchlistに追加しました。"
        }

        "substitutes an integer argument as decimal" {
            formatStringArgs("%1\$dページの保存を開始しました。", arrayOf(3)) shouldBe
                "3ページの保存を開始しました。"
        }

        "substitutes an argument in the middle of the template" {
            formatStringArgs("アップデートのインストールに失敗しました: %1\$s", arrayOf("boom")) shouldBe
                "アップデートのインストールに失敗しました: boom"
        }

        "substitutes a version string" {
            formatStringArgs("Palleria %1\$s に更新されました", arrayOf("7.4.0")) shouldBe
                "Palleria 7.4.0 に更新されました"
        }

        "leaves templates without placeholders untouched" {
            formatStringArgs("処理に失敗しました。", emptyArray()) shouldBe "処理に失敗しました。"
        }

        "renders a literal percent sign" {
            formatStringArgs("100%% 完了", emptyArray()) shouldBe "100% 完了"
        }

        "uses positional order rather than call order" {
            formatStringArgs("%2\$s と %1\$s", arrayOf("A", "B")) shouldBe "B と A"
        }

        "keeps the placeholder when the argument is missing" {
            // 表示から欠落に気付けるよう、黙って空文字にはしない。
            formatStringArgs("Palleria %1\$s に更新されました", emptyArray()) shouldBe
                "Palleria %1\$s に更新されました"
        }
    })
