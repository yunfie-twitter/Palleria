package com.yunfie.illustia.data

/** アプリ自身が占めるストレージ容量。 */
data class AppStorageUsage(
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val apkBytes: Long? = null,
    val optimizedCodeBytes: Long? = null,
    val dexMetadataBytes: Long? = null,
    val nativeLibraryBytes: Long? = null,
    val referenceProfileBytes: Long? = null,
    val currentProfileBytes: Long? = null,
) {
    val totalBytes: Long
        get() = appBytes + dataBytes

    val userDataBytes: Long
        get() = (dataBytes - cacheBytes).coerceAtLeast(0L)

    val hasCodeBreakdown: Boolean
        get() = apkBytes != null
}
