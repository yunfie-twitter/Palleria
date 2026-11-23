package com.yunfie.illustia.platform

import java.io.OutputStream

/**
 * 大きなバイナリをストリーミングで書き出す拡張境界。
 *
 * `OutputStream` は `commonMain` に持ち込めないため、[DocumentIo] 本体とは分けている。
 * Android 実装は両方を実装し、ViewModel からは `DocumentIo` として見える。
 */
interface DocumentStreamIo {
    /** 指定 URI の出力ストリームへ書き込む。 */
    fun write(
        uri: String,
        block: (OutputStream) -> Unit,
    )
}
