package com.yunfie.illustia.pallasync

internal fun authorityJsonForVerifiedExport(
    authorityJson: String,
    calculatedHash: String?,
    trustedHash: String?,
): String {
    check(!calculatedHash.isNullOrBlank() && calculatedHash == trustedHash) { "C_ROLLBACK_DETECTED" }
    return authorityJson
}
