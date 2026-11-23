package com.yunfie.illustia.platform

import android.content.Context

/** 既存の `Application` ベースの解決。移行期間中の既定実装。 */
class AndroidStringProvider(
    private val context: Context,
) : StringProvider {
    override fun get(resId: Int): String = context.getString(resId)

    override fun get(
        resId: Int,
        vararg args: Any,
    ): String = context.getString(resId, *args)
}
