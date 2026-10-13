package com.yunfie.illustia.pallasync

/** §18.7: this signs retirement only; retained copies and unreachable media are not revoked. */
internal fun signRecoveryRetirement(
    root: PallaSyncRootSession,
    store: PallaSyncRecoverySetStore,
    expected: PallaSyncRecoverySetStore.Entry,
): PallaSyncRecoverySetStore.Entry =
    store.signStatus(expected) {
        PallaSyncCore.advanceRecoveryStatus(
            root.handle,
            root.operation.wire,
            expected.manifestJson,
            expected.statusesJson,
            expected.head,
            "retired",
            null,
            System.currentTimeMillis(),
        )
    }
