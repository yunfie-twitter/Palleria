package com.yunfie.illustia.pallasync

import androidx.annotation.Keep

@Keep
@Suppress("TooManyFunctions", "LargeClass")
object PallaSyncCore {
    external fun createRecoveryResult(
        pair: Long,
        result: String,
        code: String?,
    ): String

    external fun receiveRecoveryResult(
        pair: Long,
        packetJson: String,
    ): String

    external fun validateRecoveryPackage(
        packageJson: String,
        adminPublicKey: String,
        knownStatusHash: String?,
    ): String

    external fun retryRecoveryTransfer(
        pair: Long,
        messageSequence: Long,
    ): String

    external fun encodeRecoveryPortable(
        packageJson: String,
        adminPublicKey: String,
        knownStatusHash: String?,
    ): ByteArray

    external fun decodeRecoveryPortable(
        encoded: ByteArray,
        adminPublicKey: String,
        knownStatusHash: String?,
    ): String

    external fun recoveryPackageFragments(
        packageJson: String,
        adminPublicKey: String,
        knownStatusHash: String?,
        partBytes: Int,
    ): Array<String>

    external fun assembleRecoveryFragments(
        fragments: Array<String>,
        adminPublicKey: String,
        knownStatusHash: String?,
    ): String?

    external fun advanceRecoveryStatus(
        root: Long,
        operation: String,
        manifestJson: String,
        statusesJson: String,
        knownHead: String,
        state: String,
        replacementSetId: String?,
        nowMs: Long,
    ): String

    external fun bindRecoveryReplacement(
        root: Long,
        manifestJson: String,
        statusesJson: String,
        knownHead: String,
    )

    external fun touchRecoveryCollection(collector: Long)

    external fun cancelRecoverySetSessions(setId: String)

    external fun validateRecoverySetState(
        manifestJson: String,
        statusesJson: String,
        trustedAdmin: String,
        knownHead: String?,
    ): String

    external fun prepareRecoverySet(
        root: Long,
        operation: String,
        generation: Long,
        threshold: Long,
        holdersJson: String,
        nowMs: Long,
    ): String

    external fun beginRecoveryTransfer(
        manifestJson: String,
        adminPublicKey: String,
        ceremonyId: String,
        sessionId: String,
        side: String,
        role: String,
        purpose: String,
    ): PallaSyncRecoveryCandidate

    external fun bindRecoveryTransfer(
        handle: Long,
        descriptorJson: String,
    ): String

    external fun confirmRecoveryTransfer(
        handle: Long,
        sas: String,
        explicitlyConfirmed: Boolean,
    )

    external fun endRecoveryTransfer(handle: Long)

    external fun distributeRecoveryShare(
        root: Long,
        operation: String,
        pair: Long,
        shareIndex: Long,
    ): String

    external fun receiveRecoveryDealer(
        root: Long,
        operation: String,
        pair: Long,
        packetJson: String,
    ): String

    external fun receiveRecoveryHolder(
        pair: Long,
        packetJson: String,
        holderInstanceId: String,
        holderDeviceId: String?,
        protection: String,
        storageReady: Boolean,
        replacementConfirmed: Boolean,
        occupied: Boolean,
        nowMs: Long,
    ): String

    external fun recoveryHolderPackage(
        pair: Long,
        statusesJson: String,
        credential: ByteArray?,
    ): String

    external fun recoveryHolderWrapKey(pair: Long): ByteArray

    external fun recoveryHolderPersisted(
        pair: Long,
        packageJson: String,
        readbackKey: ByteArray,
    ): String

    external fun releaseRecoveryShare(
        pair: Long,
        packageJson: String,
        wrapKey: ByteArray,
        authenticated: Boolean,
        consented: Boolean,
        locallyCompromised: Boolean,
        emergencyNewRoot: Boolean,
    ): String

    external fun beginRecoveryCollection(
        manifestJson: String,
        statusesJson: String,
        adminPublicKey: String,
        knownStatusHash: String?,
        ceremonyId: String,
        operation: String,
        setupRoot: Long,
        explicitlyConfirmed: Boolean,
        locallyCompromised: Boolean,
        emergencyNewRoot: Boolean,
    ): Long

    external fun receiveRecoveryCollector(
        collector: Long,
        pair: Long,
        packetJson: String,
    ): String

    external fun collectOwnRecoveryShare(
        collector: Long,
        packageJson: String,
        wrapKey: ByteArray,
        holderInstanceId: String,
        authenticated: Boolean,
        consented: Boolean,
        locallyCompromised: Boolean,
    ): String

    external fun finishRecoveryCollection(
        collector: Long,
        explicitlyConfirmed: Boolean,
    ): PallaSyncRecoveryCompletion

    external fun endRecoveryCollection(collector: Long)

    external fun activateRecoverySet(
        root: Long,
        operation: String,
        nowMs: Long,
    ): String

    external fun unlockOfflineRecoveryKey(
        packageJson: String,
        credential: ByteArray,
    ): ByteArray

    external fun validatePeerResponse(
        body: ByteArray,
        bindingJson: String,
        kind: String,
        limit: Long,
    ): String

    external fun validatePeerApproval(
        handle: Long,
        authorityJson: String,
        bodyJson: String,
        nowMs: Long,
    ): String

    external fun createPeerOffer(
        authorityJson: String,
        deviceId: String,
        requestJson: String,
        nowMs: Long,
    ): String

    external fun beginPeerSubject(
        offerJson: String,
        authorityJson: String,
        explicitlySelected: Boolean,
        nowMs: Long,
    ): PallaSyncPeerCandidate

    external fun beginPeerApprover(
        offerJson: String,
        authorityJson: String,
        signingSeed: ByteArray,
        kexSecret: ByteArray,
        nowMs: Long,
    ): Long

    external fun bindPeerRequest(
        handle: Long,
        requestJson: String,
        nowMs: Long,
    ): String

    external fun peerSas(
        handle: Long,
        nowMs: Long,
    ): String

    external fun confirmPeerSas(
        handle: Long,
        observed: String,
        confirmed: Boolean,
        nowMs: Long,
    )

    external fun peerCertificate(
        handle: Long,
        nowMs: Long,
    ): String

    external fun acceptPeerCertificate(
        handle: Long,
        certificateJson: String,
        nowMs: Long,
    )

    external fun validatePeerSubjectEnvelopes(
        handle: Long,
        authorityJson: String,
        certificateJson: String,
        envelopesJson: String,
        nowMs: Long,
    )

    external fun unwrapPeerSubjectEnvelope(
        handle: Long,
        authorityJson: String,
        certificateJson: String,
        envelopeJson: String,
        nowMs: Long,
    ): ByteArray

    external fun endPeerSession(handle: Long)

    external fun preparePeerEnvelope(
        handle: Long,
        authorityJson: String,
        epoch: Long,
        secret: ByteArray,
        nowMs: Long,
    ): String

    external fun preparePeerSubject(
        handle: Long,
        authorityJson: String,
        envelopeJson: String,
        metadataJson: String,
        nowMs: Long,
    ): PallaSyncPreparedKeys

    external fun validateCurrentKeyRecipient(
        authority: String,
        device: String,
    ): Boolean

    external fun deriveEpochMaterial(
        context: String,
        secret: ByteArray,
    ): PallaSyncEpochMaterial

    external fun openOwnEpochEnvelope(
        authority: String,
        entry: String,
        receipt: String,
        device: String,
        privateKey: ByteArray,
    ): ByteArray

    external fun prepareEpochBundle(
        root: Long,
        operation: String,
        authorityJson: String,
        requestJson: String,
        deviceSeed: ByteArray,
    ): PallaSyncPreparedEpoch

    /** Fresh target-generation keys and signed public objects; private material stays in Rust until copied into OS storage. */
    external fun prepareReseedBundle(
        root: Long,
        authorityJson: String,
        deviceName: String,
        createdAtMs: Long,
    ): PallaSyncPreparedReseed

    external fun validateRetainedLogLink(
        entryJson: String,
        receiptJson: String,
        pinJson: String,
        after: Long,
        previous: String,
    ): String

    external fun recoverEpochSecret(
        root: Long,
        operation: String,
        authorityJson: String,
        body: ByteArray,
        epoch: Long,
    ): ByteArray

    external fun verifyDeviceKeyDelivery(
        handle: Long,
        envelopeJson: String,
        kexPrivate: ByteArray,
    ): Boolean

    external fun validateOwnEnvelopePage(
        body: ByteArray,
        expectedJson: String,
        authorityJson: String,
    ): String

    external fun beginEpochSession(
        contextJson: String,
        epochSecret: ByteArray,
    ): Long

    external fun endEpochSession(handle: Long)

    external fun epochCollectionTag(
        handle: Long,
        collection: String,
    ): String?

    external fun decryptEpochRecord(
        handle: Long,
        recordJson: String,
    ): String

    /** §16.6: signature-check and decrypt one snapshot frame using an authenticated author key. */
    external fun decryptEpochSnapshotPart(
        handle: Long,
        recordJson: String,
        authorPublicKeyBase64Url: String,
    ): String

    external fun decryptEpochDevice(
        handle: Long,
        recordJson: String,
    ): String

    external fun validateInitialHistoryCut(
        authorityJson: String,
        body: ByteArray,
    ): String

    external fun validateReceiptResponse(
        body: ByteArray,
        entryJson: String,
        pinJson: String,
    ): String

    external fun validateRecoveryEnvelopeResponse(
        body: ByteArray,
        authorityJson: String,
        epoch: Long,
    ): String

    external fun validateEnrollmentAcceptance(
        body: ByteArray,
        submittedJson: String,
        pinJson: String,
    ): String

    external fun validateKeysAck(
        body: ByteArray,
        device: String,
        epochHash: String,
    ): String

    external fun validateEpochAcceptance(
        response: ByteArray,
        submitted: String,
        pin: String,
    ): String

    external fun validateRevocationAcceptance(
        response: ByteArray,
        submitted: String,
        pin: String,
        device: String,
    ): String

    external fun deviceCapabilityToken(
        handle: Long,
        bindingJson: String,
        body: ByteArray,
        issuedAtMs: Long,
    ): String

    external fun beginAuthorityHistory(
        body: ByteArray,
        identityJson: String,
        origin: String,
        relayKey: String,
    ): String

    external fun advanceAuthorityHistory(
        authorityJson: String,
        entryJson: String,
        receiptJson: String,
    ): String

    external fun bindAuthorityActionPayload(
        authorityJson: String,
        operationJson: String,
        payloadJson: String,
    ): String

    external fun validateLogPage(
        body: ByteArray,
        pinJson: String,
        generation: Long,
        afterSeq: Long,
        limit: Int,
    ): String

    external fun beginDeviceSession(
        signingSeed: ByteArray,
        recordKey: ByteArray,
    ): Long

    external fun endDeviceSession(handle: Long)

    /** §7.8: sign a compaction using a scoped device session after authority was verified. */
    external fun signCompactionRecord(
        handle: Long,
        compactionJson: String,
        expectedDeviceId: String,
        expectedPublicKey: String,
    ): String

    external fun signDeviceCheckpoint(
        handle: Long,
        checkpointJson: String,
        expectedDeviceId: String,
        certifiedPublicKey: String,
    ): String

    /** Strictly validates and verifies a §7.12.4 checkpoint with a certified key. */
    external fun verifyDeviceCheckpoint(
        checkpointJson: String,
        certifiedPublicKey: String,
    ): Boolean

    external fun decryptOwnPreparedRecord(
        handle: Long,
        recordJson: String,
    ): String

    external fun prepareRecordFrame(inner: ByteArray): ByteArray

    external fun requireNormalDataBoundary(input: ByteArray)

    external fun createPreparedRecord(
        handle: Long,
        contextJson: String,
        frame: ByteArray,
        nonce: ByteArray,
    ): String

    external fun validateLogHead(body: ByteArray): String

    external fun compareLogHeads(
        logJson: String,
        signedHeadJson: String,
        pinJson: String,
        generation: Long,
    ): Boolean

    external fun validateRecordAuthority(
        authorityJson: String,
        recordJson: String,
        relaySeq: Long,
    ): Boolean

    init {
        System.loadLibrary("pallasync_core")
        PallaSyncSecretLifecycle.nativeReady()
    }

    external fun validateProblem(
        body: ByteArray,
        status: Int,
    ): String

    external fun validateKnownDevice(
        authorityJson: String,
        deviceJson: String,
    ): String

    external fun observeTransparencyHead(
        body: ByteArray,
        pinJson: String,
    ): String

    external fun validateTransparencyHead(
        body: ByteArray,
        pinJson: String,
        checkpointJson: String,
    ): String

    external fun validateTransparencyPage(
        body: ByteArray,
        pinJson: String,
        anchorJson: String,
        headJson: String,
        limit: Int,
    ): String

    /** Verify a retained signed transparency suffix against an independently signed device checkpoint. */
    external fun verifyTransparencyCheckpointPrefix(
        entriesJson: String,
        transparencyId: String,
        targetIndex: Long,
        targetHash: String,
        pinJson: String,
        anchorJson: String,
    ): String

    external fun validateReceiptInclusion(
        receiptJson: String,
        entryJson: String,
        pinJson: String,
        anchorJson: String,
    ): Boolean

    external fun validateRecordsPage(
        body: ByteArray,
        pinJson: String,
        generation: Long,
        afterSeq: Long,
        limit: Int,
    ): String

    /** Generates a 24-word BIP39 English seed phrase. */
    external fun generateSeedPhrase(): String

    external fun beginRootSession(
        mnemonic: ByteArray,
        passphrase: ByteArray,
        operation: String,
        expectedChain: String?,
        expectedAdmin: String?,
    ): Long

    external fun rootIdentity(handle: Long): String

    /** Creates a §10.15 CAS-bound AdminOperation while the root stays native. */
    external fun createDeleteRequest(
        handle: Long,
        generation: Int,
        deletionId: String,
        parametersHash: String,
        epochHash: String,
        createdAtMs: Long,
    ): String

    /** Creates the exact §10.12 AdminOperation for a verified snapshot prefix. */
    external fun createPruneAdminOperation(
        handle: Long,
        currentParametersJson: String,
        currentEpochJson: String,
        compactionJson: String,
        throughSeq: Long,
        createdAtMs: Long,
    ): String

    /** Creates the exact §17.5 target-bound AdminOperation while root material stays native. */
    external fun createReseedAdminOperation(
        handle: Long,
        currentParametersJson: String,
        currentEpochJson: String,
        parametersJson: String,
        epochJson: String,
        envelopesJson: String,
        compactionJson: String,
        deviceRecordsJson: String,
        createdAtMs: Long,
    ): String

    external fun validateDeletionProof(
        body: ByteArray,
        pinJson: String,
        authorityJson: String,
        submittedRequestJson: String,
    ): String

    /** Validates an optional §7.12.5 witness bundle against a verified relay receipt and authority checkpoint. */
    external fun validateDeletionEvidenceBundle(
        receiptJson: String,
        bundleJson: String,
        authorityJson: String,
    ): String

    /** Validates monotone signed §7.12.3 completion revisions without changing deletion authority. */
    external fun validateDeletionReceiptRevision(
        previousReceiptJson: String,
        nextReceiptJson: String,
        pinJson: String,
    ): String

    external fun createDeletionWitness(
        receiptJson: String,
        authorityJson: String,
        deviceId: String,
        signingSeed: ByteArray,
        createdAtMs: Long,
    ): String

    external fun rootCapabilityToken(
        handle: Long,
        operation: String,
        aud: String,
        method: String,
        path: String,
        query: String,
        body: ByteArray,
        issuedAtMs: Long,
    ): String?

    external fun touchRootSession(handle: Long)

    external fun endRootSession(handle: Long)

    external fun endSecretSessions()

    external fun setSecretForeground(foreground: Boolean)

    external fun objectHash(valueJson: String): String?

    external fun canonicalJson(valueJson: String): String?

    external fun validateRecordUpload(
        body: ByteArray,
        submitted: String,
        pin: String,
    ): String

    /** Validates §10.13 per-record receipts plus its required repair_required result. */
    external fun validateRecordRepair(
        body: ByteArray,
        submitted: String,
        pin: String,
    ): String

    external fun validateGenesisAcceptance(
        body: ByteArray,
        genesis: String,
        identity: String,
        origin: String,
        key: String,
    ): String

    /** Verifies exact §17.5 data-log receipt order and leaves transparency target advancement to suffix validation. */
    external fun validateReseedCommitAcceptance(
        body: ByteArray,
        payload: String,
        adminOperation: String,
        currentParameters: String,
        currentEpoch: String,
        expectedEntries: String,
        pin: String,
    ): String

    external fun validateHealth(
        health: ByteArray,
        origin: String,
    ): String

    external fun validatePolicy(
        markdown: ByteArray,
        disclosure: ByteArray,
        health: ByteArray,
        origin: String,
        pinnedKey: String,
        highestRevision: Long,
        highestHash: String,
        nowMs: Long,
    ): String

    /** Verifies the SyncRecord Ed25519 signature (§7.1, §4.1). */
    external fun verifySyncRecord(
        recordJson: String,
        devicePublicKeyBase64: String,
    ): Boolean

    /** Verifies an immutable legacy v2.1 SyncRecord only for explicit §9 migration. */
    external fun verifyLegacySyncRecordV21(
        recordJson: String,
        devicePublicKeyBase64: String,
    ): Boolean

    /** Verifies a self-signed v2.1 registry row before legacy migration uses its device key (§9.1). */
    external fun verifyLegacyDeviceRecordV21(recordJson: String): Boolean

    /** Signs only a v2.1 read capability for the user's explicitly selected legacy source (§9.1). */
    external fun legacyV21GetToken(
        deviceSeedBase64: ByteArray,
        chainId: String,
        deviceId: String,
        path: String,
        query: String,
        nowMs: Long,
    ): String

    /** Derives the legacy v2.1 identity in a transient native operation and compares public pins. */
    external fun validateLegacyV21Identity(
        seedPhrase: ByteArray,
        passphrase: ByteArray,
        chainId: String,
        chainSaltBase64: String,
        adminPublicKeyBase64: String,
    ): Boolean

    /** Explicit §9.1 migration session; verified payload bytes must be zeroed by the caller. */
    external fun beginLegacyMigrationSession(
        seedPhrase: ByteArray,
        passphrase: ByteArray,
        chainSaltBase64: String,
        chainId: String,
        adminPublicKeyBase64: String,
    ): Long

    /** Deterministic v2.0 legacy key path, isolated from the v2.1 random-salt derivation. */
    external fun beginLegacyV20MigrationSession(
        seedPhrase: ByteArray,
        passphrase: ByteArray,
        chainId: String,
    ): Long

    external fun convertLegacyRecord(
        handle: Long,
        recordJson: String,
        expectedDeviceId: String,
        devicePublicKeyBase64: String,
    ): ByteArray

    external fun endLegacyMigrationSession(handle: Long)

    /** Verifies a device record against its embedded public key. */
    external fun verifyDeviceRecord(recordJson: String): Boolean

    /**
     * Creates a complete Genesis Bundle (§10.2).
     * Returns owned mutable keys and a public signed genesis request.
     */
    external fun createGenesisBundle(
        rootSession: Long,
        deviceName: String,
        keyProtection: String = "os-keystore",
        aud: String,
    ): PallaSyncPreparedKeys

    /**
     * Creates a complete Mnemonic Enrollment Bundle (§10.4, §10.8).
     * Returns owned mutable keys and public enrollment/acknowledgement requests.
     */
    external fun createMnemonicEnrollmentBundle(
        rootSession: Long,
        deviceName: String,
        keyProtection: String = "os-keystore",
        generation: Long,
        epoch: Long,
        recoveryEnvelopesJson: String,
        expectedParametersHash: String,
        expectedEpochHash: String,
        aud: String,
    ): PallaSyncPreparedKeys

    /** Forces class initialization so feature-specific JNI facades can share this library. */
    internal fun ensureNativeLibraryLoaded() = Unit
}
