package com.yunfie.illustia.models

import androidx.annotation.StringRes
import com.yunfie.illustia.R

/** フィード種別の UI ラベル。 */
@get:StringRes
val HomeFeedKind.labelResId: Int
    get() =
        when (this) {
            HomeFeedKind.Recommended -> R.string.feed_recommended
            HomeFeedKind.Ranking -> R.string.feed_ranking
            HomeFeedKind.New -> R.string.feed_new
        }

/** 公開範囲の UI ラベル。 */
@get:StringRes
val Restrict.labelResId: Int
    get() =
        when (this) {
            Restrict.Public -> R.string.restrict_public
            Restrict.Private -> R.string.restrict_private
        }
