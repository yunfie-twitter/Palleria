package com.yunfie.illustia.models

import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.feed_new
import com.yunfie.illustia.resources.feed_ranking
import com.yunfie.illustia.resources.feed_recommended
import com.yunfie.illustia.resources.restrict_private
import com.yunfie.illustia.resources.restrict_public
import org.jetbrains.compose.resources.StringResource

/** フィード種別の UI ラベル。 */
val HomeFeedKind.labelResId: StringResource
    get() =
        when (this) {
            HomeFeedKind.Recommended -> Res.string.feed_recommended
            HomeFeedKind.Ranking -> Res.string.feed_ranking
            HomeFeedKind.New -> Res.string.feed_new
        }

/** 公開範囲の UI ラベル。 */
val Restrict.labelResId: StringResource
    get() =
        when (this) {
            Restrict.Public -> Res.string.restrict_public
            Restrict.Private -> Res.string.restrict_private
        }
