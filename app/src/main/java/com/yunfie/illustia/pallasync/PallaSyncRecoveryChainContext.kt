package com.yunfie.illustia.pallasync

/** Public, OS-pinned chain identity for local recovery configuration (§§7.11/18.6). */
internal data class PallaSyncRecoveryChainContext(
    val chainId: String,
    val adminPublicKey: String,
    val generation: Long,
    val deviceId: String,
)
