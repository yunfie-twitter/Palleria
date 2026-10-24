package com.yunfie.illustia.pallasync

/** Passes legacy root inputs across JNI as owned UTF-8 bytes and clears both arrays on every exit. */
internal inline fun <T> withLegacySecretInputs(
    mnemonic: String,
    passphrase: String,
    block: (mnemonicBytes: ByteArray, passphraseBytes: ByteArray) -> T,
): T {
    val mnemonicBytes = strictRootUtf8(mnemonic)
    try {
        val passphraseBytes = strictRootUtf8(passphrase)
        try {
            return block(mnemonicBytes, passphraseBytes)
        } finally {
            passphraseBytes.fill(0)
        }
    } finally {
        mnemonicBytes.fill(0)
    }
}
