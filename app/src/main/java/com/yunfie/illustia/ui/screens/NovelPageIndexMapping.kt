package com.yunfie.illustia.ui.screens

/**
 * 連続スクロール（[NovelLayoutMode.Scroll]）での「ページ ⇔ LazyList のアイテムインデックス」変換。
 *
 * 1 ページは LazyColumn 上では次の並びになる:
 *   [ページ見出し] + [各ブロック] + ([ページ区切り] ※最終ページ以外)
 * そのため 1 ページ分のアイテム数は `1 + blocks.size + (最終ページ以外なら 1)` になる。
 *
 * この計算は以前 [NovelScreen.kt] 内に 4 箇所コピーされていた。
 * オフバイワンが起きやすいので、ここに集約して全呼び出しをこの純関数に寄せる。
 */
internal object NovelPageIndexMapping {
    /** 1 ページが LazyColumn 上で占めるアイテム数。 */
    fun itemCountForPage(
        pages: List<NovelPage>,
        pageIndex: Int,
    ): Int {
        val blocks = pages[pageIndex].blocks.size
        val divider = if (pageIndex < pages.lastIndex) 1 else 0
        return 1 + blocks + divider
    }

    /** ページ先頭が対応する LazyColumn のアイテムインデックス。 */
    fun pageToItemIndex(
        pages: List<NovelPage>,
        pageIndex: Int,
    ): Int {
        if (pageIndex <= 0) return 0
        var index = 0
        for (i in 0 until pageIndex.coerceAtMost(pages.size)) {
            index += itemCountForPage(pages, i)
        }
        return index
    }

    /**
     * ページ内の指定ブロックが対応するアイテムインデックス。
     * ページ見出しが 1 個先頭にあるため `1 + blockIndexInPage` を加算する。
     */
    fun blockToItemIndex(
        pages: List<NovelPage>,
        pageIndex: Int,
        blockIndexInPage: Int,
    ): Int = pageToItemIndex(pages, pageIndex) + 1 + blockIndexInPage

    /**
     * LazyColumn の先頭可視アイテムからページ番号を逆引きする。
     * 範囲外なら 0..lastIndex にクランプする。
     */
    fun itemToPage(
        pages: List<NovelPage>,
        firstVisibleItemIndex: Int,
    ): Int {
        var count = 0
        var page = pages.lastIndex.coerceAtLeast(0)
        if (pages.isNotEmpty()) {
            for (i in pages.indices) {
                count += itemCountForPage(pages, i)
                if (firstVisibleItemIndex < count) {
                    page = i
                    break
                }
            }
        }
        return page
    }
}
