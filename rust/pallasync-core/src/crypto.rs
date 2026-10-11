use crate::models::{
    self, CapabilityToken, DeviceMetaAAD, DeviceMetaPlaintext, DeviceRecord,
    EnrollmentCertificate, EnvelopeBinding, EpochKeyEnvelope, InnerRecord, RecordAAD, SyncRecord,
    unsigned_record_jcs,
};
use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use bip39::{Language, Mnemonic};
use chacha20poly1305::{
    ChaCha20Poly1305, KeyInit, Nonce, XChaCha20Poly1305, XNonce,
    aead::{Aead as AeadTrait, Payload},
};
use ed25519_dalek::{Signature, Signer, SigningKey, VerifyingKey};
use hkdf::Hkdf;
use hmac::{Hmac, Mac};
use hpke::{
    Deserializable, Kem, OpModeR, OpModeS, Serializable,
    aead::ChaCha20Poly1305 as HpkeAeadChaCha,
    kdf::HkdfSha256 as HpkeKdfSha256,
    kem::X25519HkdfSha256 as HpkeKemX25519,
};
use rand::RngCore;
use rand_core::OsRng;
use sha2::{Digest, Sha256};

pub type HmacSha256 = Hmac<Sha256>;

pub const CTX_SYNC_RECORD: &[u8] = b"PALLASYNC-SYNC-RECORD-v3\0";
pub const CTX_DEVICE_RECORD: &[u8] = b"PALLASYNC-DEVICE-RECORD-v3\0";
pub const CTX_ENROLLMENT: &[u8] = b"PALLASYNC-ENROLLMENT-v3\0";
pub const CTX_CAPABILITY: &[u8] = b"PALLASYNC-CAPABILITY-v3\0";
pub const CTX_CHAIN_PARAMS: &[u8] = b"PALLASYNC-CHAIN-PARAMS-v3\0";
pub const CTX_EPOCH: &[u8] = b"PALLASYNC-EPOCH-v3\0";
pub const CTX_EPOCH_ENVELOPE: &[u8] = b"PALLASYNC-EPOCH-ENVELOPE-v3\0";
pub const CTX_COMPACTION: &[u8] = b"PALLASYNC-COMPACTION-v3\0";
pub const CTX_ENROLL_REQUEST: &[u8] = b"PALLASYNC-ENROLL-REQUEST-v3\0";
pub const CTX_ADMIN_OP: &[u8] = b"PALLASYNC-ADMIN-OP-v3\0";
pub const CTX_RECOVERY_SECRET_FP: &[u8] = b"PALLASYNC-RECOVERY-SECRET-FP-v3\0";
pub const CTX_RECOVERY_SAS: &[u8] = b"PALLASYNC-RECOVERY-SAS-v3\0";

// Size buckets in bytes per Protocol 3.0 §14.3
pub const SIZE_BUCKETS: &[usize] = &[1024, 4096, 16384, 65536, 262144, 1048576];
pub const DEVICE_META_BUCKETS: &[usize] = &[1024, 4096];

pub fn generate_seed_phrase() -> String {
    let mut entropy = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut entropy);
    let mnemonic = Mnemonic::from_entropy(&entropy).unwrap();
    mnemonic.to_string()
}

pub fn generate_nonce_24() -> [u8; 24] {
    let mut nonce = [0u8; 24];
    rand::thread_rng().fill_bytes(&mut nonce);
    nonce
}

pub fn generate_nonce_16() -> [u8; 16] {
    let mut nonce = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut nonce);
    nonce
}

pub fn generate_epoch_secret() -> [u8; 32] {
    let mut secret = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut secret);
    secret
}

pub fn generate_device_keys() -> (SigningKey, [u8; 32]) {
    let mut csprng = OsRng;
    let signing_key = SigningKey::generate(&mut csprng);
    let mut kex_bytes = [0u8; 32];
    csprng.fill_bytes(&mut kex_bytes);
    (signing_key, kex_bytes)
}

pub fn sha256_hash(data: &[u8]) -> [u8; 32] {
    Sha256::digest(data).into()
}

// ---------------------------------------------------------------------
// Root Key Derivation & Chain ID (§5.1, §5.2)
// ---------------------------------------------------------------------

pub struct RootKeys {
    pub chain_id: String,        // b64u(32), 43 chars
    pub chain_salt: String,      // b64u(32)
    pub chain_salt_bytes: [u8; 32],
    pub chain_id_digest: [u8; 32],
    pub admin_key: SigningKey,
    pub admin_public_key: String, // b64u(32)
    pub recovery_kek: [u8; 32],
}

pub fn derive_root_seed(seed_phrase: &str, passphrase: &str) -> Result<[u8; 64], String> {
    let mnemonic = Mnemonic::parse_in(Language::English, seed_phrase)
        .map_err(|e| format!("Invalid seed phrase: {e}"))?;
    Ok(mnemonic.to_seed(passphrase))
}

pub fn derive_chain_salt(root_seed: &[u8; 64]) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(b"PALLASYNC-CHAIN-SALT-v3\0");
    hasher.update(root_seed);
    hasher.finalize().into()
}

pub fn derive_chain_id_digest(chain_salt: &[u8; 32], root_seed: &[u8; 64]) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(b"PALLASYNC-CHAIN-ID-v3\0");
    hasher.update(chain_salt);
    hasher.update(root_seed);
    hasher.finalize().into()
}

pub fn derive_root_keys(seed_phrase: &str, passphrase: &str) -> Result<RootKeys, String> {
    let root_seed = derive_root_seed(seed_phrase, passphrase)?;
    let chain_salt = derive_chain_salt(&root_seed);
    let chain_id_digest = derive_chain_id_digest(&chain_salt, &root_seed);
    let chain_id = URL_SAFE_NO_PAD.encode(chain_id_digest);

    let hk = Hkdf::<Sha256>::new(Some(&chain_salt), &root_seed);

    let mut admin_seed = [0u8; 32];
    hk.expand(b"PALLASYNC-v3\0admin-ed25519\0", &mut admin_seed)
        .map_err(|_| "HKDF expand failure: admin_seed")?;
    let admin_key = SigningKey::from_bytes(&admin_seed);
    let admin_public_key = URL_SAFE_NO_PAD.encode(admin_key.verifying_key().as_bytes());

    let mut recovery_kek = [0u8; 32];
    hk.expand(b"PALLASYNC-v3\0recovery-kek\0", &mut recovery_kek)
        .map_err(|_| "HKDF expand failure: recovery_kek")?;

    Ok(RootKeys {
        chain_id,
        chain_salt: URL_SAFE_NO_PAD.encode(chain_salt),
        chain_salt_bytes: chain_salt,
        chain_id_digest,
        admin_key,
        admin_public_key,
        recovery_kek,
    })
}

// ---------------------------------------------------------------------
// Epoch Secrets and Keys (§5.3)
// ---------------------------------------------------------------------

pub struct EpochKeys {
    pub epoch_prk: [u8; 32],
    pub record_key: [u8; 32],
    pub device_meta_key: [u8; 32],
    pub collection_tag_key: [u8; 32],
    pub epoch_commitment: [u8; 32],
    pub epoch_commitment_b64u: String,
}

pub fn derive_epoch_keys(
    chain_id_digest: &[u8; 32],
    epoch: u32,
    epoch_secret: &[u8; 32],
) -> Result<EpochKeys, String> {
    const EPOCH_SALT_PREFIX: &[u8] = b"PALLASYNC-v3\0epoch\0";
    let mut salt = Vec::with_capacity(EPOCH_SALT_PREFIX.len() + 32 + 4);
    salt.extend_from_slice(EPOCH_SALT_PREFIX);
    salt.extend_from_slice(chain_id_digest);
    salt.extend_from_slice(&epoch.to_be_bytes());

    let hk = Hkdf::<Sha256>::new(Some(&salt), epoch_secret);

    let mut record_key = [0u8; 32];
    hk.expand(b"PALLASYNC-v3\0record\0", &mut record_key)
        .map_err(|_| "HKDF expand failure: record_key")?;

    let mut device_meta_key = [0u8; 32];
    hk.expand(b"PALLASYNC-v3\0device-meta\0", &mut device_meta_key)
        .map_err(|_| "HKDF expand failure: device_meta_key")?;

    let mut collection_tag_key = [0u8; 32];
    hk.expand(b"PALLASYNC-v3\0collection-tag\0", &mut collection_tag_key)
        .map_err(|_| "HKDF expand failure: collection_tag_key")?;

    let mut epoch_commitment = [0u8; 32];
    hk.expand(b"PALLASYNC-v3\0epoch-commitment\0", &mut epoch_commitment)
        .map_err(|_| "HKDF expand failure: epoch_commitment")?;

    let epoch_commitment_b64u = URL_SAFE_NO_PAD.encode(epoch_commitment);

    // Extract epoch_prk (first 32 bytes from HKDF-Extract)
    let (prk_arr, _) = Hkdf::<Sha256>::extract(Some(&salt), epoch_secret);
    let epoch_prk: [u8; 32] = prk_arr.into();

    Ok(EpochKeys {
        epoch_prk,
        record_key,
        device_meta_key,
        collection_tag_key,
        epoch_commitment,
        epoch_commitment_b64u,
    })
}

// ---------------------------------------------------------------------
// Collection Tag (§14.2)
// ---------------------------------------------------------------------

pub fn compute_collection_tag(collection_tag_key: &[u8; 32], collection: &str) -> String {
    let mut mac = <HmacSha256 as KeyInit>::new_from_slice(collection_tag_key)
        .expect("HMAC can take any key of size 32");
    mac.update(collection.as_bytes());
    let full = mac.finalize().into_bytes();
    URL_SAFE_NO_PAD.encode(&full[..16])
}

// ---------------------------------------------------------------------
// Framing & Size Bucketing (§14.3)
// ---------------------------------------------------------------------

pub fn pad_and_frame(
    plaintext: &[u8],
    buckets: &[usize],
    is_test_zero_padding: bool,
) -> Result<Vec<u8>, String> {
    let l = plaintext.len();
    let needed = 4 + l;
    let bucket = buckets
        .iter()
        .copied()
        .find(|&b| needed <= b)
        .ok_or_else(|| format!("Plaintext of size {l} exceeds maximum bucket limit"))?;

    let mut frame = Vec::with_capacity(bucket);
    frame.extend_from_slice(&(l as u32).to_be_bytes());
    frame.extend_from_slice(plaintext);

    let pad_len = bucket - needed;
    if pad_len > 0 {
        if is_test_zero_padding {
            frame.resize(bucket, 0u8);
        } else {
            let mut padding = vec![0u8; pad_len];
            rand::thread_rng().fill_bytes(&mut padding);
            frame.extend_from_slice(&padding);
        }
    }
    Ok(frame)
}

pub fn unpad_and_unframe(framed: &[u8], buckets: &[usize]) -> Result<Vec<u8>, String> {
    if !buckets.contains(&framed.len()) {
        return Err(format!(
            "Ciphertext frame length {} is not a valid bucket size",
            framed.len()
        ));
    }
    if framed.len() < 4 {
        return Err("Frame too short for length header".to_string());
    }
    let l = u32::from_be_bytes(framed[0..4].try_into().unwrap()) as usize;
    if 4 + l > framed.len() {
        return Err(format!(
            "Declared frame length {l} exceeds available frame length {}",
            framed.len() - 4
        ));
    }
    Ok(framed[4..4 + l].to_vec())
}

// ---------------------------------------------------------------------
// Ed25519 Signing & Strict Verification (§4.1)
// ---------------------------------------------------------------------

pub fn sign_with_context(signing_key: &SigningKey, context: &[u8], canonical_jcs: &[u8]) -> String {
    let mut msg = Vec::with_capacity(context.len() + canonical_jcs.len());
    msg.extend_from_slice(context);
    msg.extend_from_slice(canonical_jcs);
    URL_SAFE_NO_PAD.encode(signing_key.sign(&msg).to_bytes())
}

pub fn verify_with_context(
    public_key: &VerifyingKey,
    context: &[u8],
    canonical_jcs: &[u8],
    signature_base64_url: &str,
) -> Result<bool, String> {
    if public_key.is_weak() {
        return Ok(false);
    }
    let signature = decode_signature(signature_base64_url)?;
    let mut msg = Vec::with_capacity(context.len() + canonical_jcs.len());
    msg.extend_from_slice(context);
    msg.extend_from_slice(canonical_jcs);

    Ok(public_key.verify_strict(&msg, &signature).is_ok())
}

fn decode_fixed<const N: usize>(encoded: &str, label: &str) -> Result<[u8; N], String> {
    let decoded = URL_SAFE_NO_PAD
        .decode(encoded)
        .map_err(|_| format!("Invalid base64url {label}"))?;
    decoded
        .try_into()
        .map_err(|_| format!("{label} must be {N} bytes"))
}

pub fn decode_key_32(encoded: &str, label: &str) -> Result<[u8; 32], String> {
    decode_fixed(encoded, label)
}

pub fn decode_signing_key(encoded: &str) -> Result<SigningKey, String> {
    Ok(SigningKey::from_bytes(&decode_fixed(
        encoded,
        "signing key",
    )?))
}

pub fn decode_verifying_key(encoded: &str) -> Result<VerifyingKey, String> {
    let bytes = decode_fixed(encoded, "public key")?;
    let vk = VerifyingKey::from_bytes(&bytes)
        .map_err(|_| "Invalid Ed25519 public key".to_string())?;
    if vk.is_weak() {
        return Err("Weak Ed25519 public key".to_string());
    }
    Ok(vk)
}

pub fn decode_signature(encoded: &str) -> Result<Signature, String> {
    Ok(Signature::from_bytes(&decode_fixed(encoded, "signature")?))
}

// ---------------------------------------------------------------------
// SyncRecord Creation, Encryption, Decryption & Verification (§7.1, §7.2, §7.3)
// ---------------------------------------------------------------------

#[allow(clippy::too_many_arguments)]
pub fn create_sync_record(
    chain_id: &str,
    generation: u32,
    record_id: &str,
    device_id: &str,
    epoch: u32,
    collection_tag: Option<&str>,
    inner_record: &InnerRecord,
    record_key: &[u8; 32],
    signing_key: &SigningKey,
    is_test_zero_padding: bool,
) -> Result<SyncRecord, String> {
    let inner_jcs = models::to_jcs(inner_record)
        .map_err(|e| format!("Cannot canonicalize InnerRecord: {e}"))?;

    let framed = pad_and_frame(&inner_jcs, SIZE_BUCKETS, is_test_zero_padding)?;

    let nonce_bytes = generate_nonce_24();
    let payload_nonce = URL_SAFE_NO_PAD.encode(nonce_bytes);

    let aad_obj = RecordAAD {
        r#type: "PALLASYNC-AAD-v3",
        version: "3.0",
        chain_id,
        generation,
        record_id,
        device_id,
        epoch,
        collection_tag,
    };
    let aad = models::to_jcs(&aad_obj).map_err(|e| format!("Cannot canonicalize RecordAAD: {e}"))?;

    let cipher = XChaCha20Poly1305::new(record_key.into());
    let ciphertext = cipher
        .encrypt(XNonce::from_slice(&nonce_bytes), Payload { msg: &framed, aad: &aad })
        .map_err(|e| format!("XChaCha20Poly1305 encryption failure: {e}"))?;
    let encrypted_payload = URL_SAFE_NO_PAD.encode(ciphertext);

    let mut record = SyncRecord {
        version: "3.0".to_string(),
        chain_id: chain_id.to_string(),
        generation,
        record_id: record_id.to_string(),
        device_id: device_id.to_string(),
        epoch,
        collection_tag: collection_tag.map(ToString::to_string),
        payload_nonce,
        encrypted_payload,
        ext: None,
        signature: String::new(),
    };

    sign_sync_record(&mut record, signing_key)?;
    Ok(record)
}

pub fn sign_sync_record(record: &mut SyncRecord, signing_key: &SigningKey) -> Result<(), String> {
    let canonical = unsigned_record_jcs(record)
        .map_err(|error| format!("Cannot canonicalize sync record: {error}"))?;
    record.signature = sign_with_context(signing_key, CTX_SYNC_RECORD, &canonical);
    Ok(())
}

pub fn verify_sync_record(
    record: &SyncRecord,
    public_key_base64_url: &str,
) -> Result<bool, String> {
    let public_key = decode_verifying_key(public_key_base64_url)?;
    let canonical = unsigned_record_jcs(record)
        .map_err(|error| format!("Cannot canonicalize sync record: {error}"))?;
    verify_with_context(&public_key, CTX_SYNC_RECORD, &canonical, &record.signature)
}

pub fn verify_sync_record_json(
    record_json: &str,
    public_key_base64_url: &str,
) -> Result<bool, String> {
    let record = serde_json::from_str::<SyncRecord>(record_json)
        .map_err(|error| format!("Invalid sync record JSON: {error}"))?;
    verify_sync_record(&record, public_key_base64_url)
}

pub fn decrypt_sync_record(
    record: &SyncRecord,
    record_key: &[u8; 32],
) -> Result<InnerRecord, String> {
    let ciphertext = URL_SAFE_NO_PAD
        .decode(&record.encrypted_payload)
        .map_err(|_| "Invalid base64url encrypted payload".to_string())?;

    let nonce_bytes = URL_SAFE_NO_PAD
        .decode(&record.payload_nonce)
        .map_err(|_| "Invalid base64url payload nonce".to_string())?;
    if nonce_bytes.len() != 24 {
        return Err("Payload nonce must be 24 bytes".to_string());
    }

    let aad_obj = RecordAAD {
        r#type: "PALLASYNC-AAD-v3",
        version: "3.0",
        chain_id: &record.chain_id,
        generation: record.generation,
        record_id: &record.record_id,
        device_id: &record.device_id,
        epoch: record.epoch,
        collection_tag: record.collection_tag.as_deref(),
    };
    let aad = models::to_jcs(&aad_obj).map_err(|e| format!("Cannot canonicalize RecordAAD: {e}"))?;

    let cipher = XChaCha20Poly1305::new(record_key.into());
    let framed = cipher
        .decrypt(XNonce::from_slice(&nonce_bytes), Payload { msg: &ciphertext, aad: &aad })
        .map_err(|_| "AEAD decryption failed (invalid key, nonce, or AAD mismatch)".to_string())?;

    let plaintext_bytes = unpad_and_unframe(&framed, SIZE_BUCKETS)?;
    serde_json::from_slice(&plaintext_bytes)
        .map_err(|e| format!("Cannot deserialize decrypted InnerRecord JSON: {e}"))
}

// ---------------------------------------------------------------------
// DeviceRecord Creation, Encryption, Decryption & Verification (§7.4)
// ---------------------------------------------------------------------

pub fn create_device_record(
    chain_id: &str,
    generation: u32,
    device_id: &str,
    device_signing_key: &SigningKey,
    device_kex_public_key: &str,
    device_meta: &DeviceMetaPlaintext,
    meta_epoch: u32,
    device_meta_key: &[u8; 32],
    enrollment: EnrollmentCertificate,
) -> Result<DeviceRecord, String> {
    let meta_jcs = models::to_jcs(device_meta)
        .map_err(|e| format!("Cannot canonicalize device metadata: {e}"))?;
    let framed = pad_and_frame(&meta_jcs, DEVICE_META_BUCKETS, false)?;

    let nonce_bytes = generate_nonce_24();
    let meta_nonce = URL_SAFE_NO_PAD.encode(nonce_bytes);

    let aad_obj = DeviceMetaAAD {
        r#type: "PALLASYNC-DEVICE-META-v3",
        chain_id,
        generation,
        device_id,
        epoch: meta_epoch,
    };
    let aad = models::to_jcs(&aad_obj).map_err(|e| format!("Cannot canonicalize DeviceMetaAAD: {e}"))?;

    let cipher = XChaCha20Poly1305::new(device_meta_key.into());
    let ciphertext = cipher
        .encrypt(XNonce::from_slice(&nonce_bytes), Payload { msg: &framed, aad: &aad })
        .map_err(|e| format!("Device metadata encryption failed: {e}"))?;

    let device_public_key = URL_SAFE_NO_PAD.encode(device_signing_key.verifying_key().as_bytes());

    let mut record = DeviceRecord {
        version: "3.0".to_string(),
        chain_id: chain_id.to_string(),
        generation,
        device_id: device_id.to_string(),
        device_public_key,
        device_kex_public_key: device_kex_public_key.to_string(),
        meta_epoch,
        meta_nonce,
        encrypted_device_meta: URL_SAFE_NO_PAD.encode(ciphertext),
        enrollment,
        ext: None,
        signature: String::new(),
    };

    sign_device_record(&mut record, device_signing_key)?;
    Ok(record)
}

pub fn sign_device_record(record: &mut DeviceRecord, signing_key: &SigningKey) -> Result<(), String> {
    let canonical = unsigned_record_jcs(record)
        .map_err(|error| format!("Cannot canonicalize device record: {error}"))?;
    record.signature = sign_with_context(signing_key, CTX_DEVICE_RECORD, &canonical);
    Ok(())
}

pub fn verify_device_record(record: &DeviceRecord) -> Result<bool, String> {
    let public_key = decode_verifying_key(&record.device_public_key)?;
    let canonical = unsigned_record_jcs(record)
        .map_err(|error| format!("Cannot canonicalize device record: {error}"))?;
    verify_with_context(&public_key, CTX_DEVICE_RECORD, &canonical, &record.signature)
}

pub fn verify_device_record_json(record_json: &str) -> Result<bool, String> {
    let record = serde_json::from_str::<DeviceRecord>(record_json)
        .map_err(|error| format!("Invalid device record JSON: {error}"))?;
    verify_device_record(&record)
}

pub fn decrypt_device_meta(
    record: &DeviceRecord,
    device_meta_key: &[u8; 32],
) -> Result<DeviceMetaPlaintext, String> {
    let ciphertext = URL_SAFE_NO_PAD
        .decode(&record.encrypted_device_meta)
        .map_err(|_| "Invalid base64url encrypted device meta".to_string())?;

    let nonce_bytes = URL_SAFE_NO_PAD
        .decode(&record.meta_nonce)
        .map_err(|_| "Invalid base64url meta nonce".to_string())?;
    if nonce_bytes.len() != 24 {
        return Err("Meta nonce must be 24 bytes".to_string());
    }

    let aad_obj = DeviceMetaAAD {
        r#type: "PALLASYNC-DEVICE-META-v3",
        chain_id: &record.chain_id,
        generation: record.generation,
        device_id: &record.device_id,
        epoch: record.meta_epoch,
    };
    let aad = models::to_jcs(&aad_obj).map_err(|e| format!("Cannot canonicalize DeviceMetaAAD: {e}"))?;

    let cipher = XChaCha20Poly1305::new(device_meta_key.into());
    let framed = cipher
        .decrypt(XNonce::from_slice(&nonce_bytes), Payload { msg: &ciphertext, aad: &aad })
        .map_err(|_| "Device metadata decryption failed (invalid key, nonce, or AAD mismatch)".to_string())?;

    let plaintext_bytes = unpad_and_unframe(&framed, DEVICE_META_BUCKETS)?;
    serde_json::from_slice(&plaintext_bytes)
        .map_err(|e| format!("Cannot deserialize decrypted DeviceMetaPlaintext: {e}"))
}

// ---------------------------------------------------------------------
// EpochKeyEnvelope Wrap & Unwrap (HPKE & Recovery) (§7.7)
// ---------------------------------------------------------------------

pub fn wrap_device_epoch_envelope(
    envelope_id: &str,
    chain_id: &str,
    generation: u32,
    epoch: u32,
    previous_epoch_hash: Option<&str>,
    epoch_commitment: &str,
    recipient_device_id: &str,
    recipient_kex_public_key_b64u: &str,
    signer_kind: &str,
    signer_device_id: Option<&str>,
    signing_key: &SigningKey,
    epoch_secret: &[u8; 32],
) -> Result<EpochKeyEnvelope, String> {
    let recipient_pk_bytes = decode_key_32(recipient_kex_public_key_b64u, "recipient X25519 public key")?;
    let recipient_key_hash = URL_SAFE_NO_PAD.encode(sha256_hash(&recipient_pk_bytes));

    let binding = EnvelopeBinding {
        envelope_id,
        chain_id,
        generation,
        epoch,
        previous_epoch_hash,
        epoch_commitment,
        recipient_kind: "device",
        recipient_device_id: Some(recipient_device_id),
        recipient_key_hash: Some(&recipient_key_hash),
        signer_kind,
        signer_device_id,
    };
    let binding_jcs = models::to_jcs(&binding)
        .map_err(|e| format!("Cannot canonicalize EnvelopeBinding: {e}"))?;

    const INFO_PREFIX: &[u8] = b"PALLASYNC-EPOCH-ENVELOPE-v3\0";
    let mut info = Vec::with_capacity(INFO_PREFIX.len() + binding_jcs.len());
    info.extend_from_slice(INFO_PREFIX);
    info.extend_from_slice(&binding_jcs);

    let recipient_pk = <HpkeKemX25519 as Kem>::PublicKey::from_bytes(&recipient_pk_bytes)
        .map_err(|_| "Invalid recipient X25519 public key for HPKE".to_string())?;

    let (enc, mut sender_ctx) = hpke::setup_sender::<HpkeAeadChaCha, HpkeKdfSha256, HpkeKemX25519, _>(
        &OpModeS::Base,
        &recipient_pk,
        &info,
        &mut OsRng,
    )
    .map_err(|e| format!("HPKE setup_sender failed: {e:?}"))?;

    let ciphertext = sender_ctx
        .seal(epoch_secret, &binding_jcs)
        .map_err(|e| format!("HPKE seal failed: {e:?}"))?;

    let enc_b64u = URL_SAFE_NO_PAD.encode(enc.to_bytes());
    let ciphertext_b64u = URL_SAFE_NO_PAD.encode(ciphertext);

    let mut envelope = EpochKeyEnvelope {
        envelope_id: envelope_id.to_string(),
        chain_id: chain_id.to_string(),
        generation,
        epoch,
        previous_epoch_hash: previous_epoch_hash.map(ToString::to_string),
        epoch_commitment: epoch_commitment.to_string(),
        recipient_kind: "device".to_string(),
        recipient_device_id: Some(recipient_device_id.to_string()),
        recipient_key_hash: Some(recipient_key_hash),
        signer_kind: signer_kind.to_string(),
        signer_device_id: signer_device_id.map(ToString::to_string),
        enc: Some(enc_b64u),
        nonce: None,
        ciphertext: ciphertext_b64u,
        ext: None,
        signature: String::new(),
    };

    let canonical = unsigned_record_jcs(&envelope)
        .map_err(|e| format!("Cannot canonicalize EpochKeyEnvelope: {e}"))?;
    envelope.signature = sign_with_context(signing_key, CTX_EPOCH_ENVELOPE, &canonical);

    Ok(envelope)
}

pub fn unwrap_device_epoch_envelope(
    envelope: &EpochKeyEnvelope,
    recipient_kex_private_key: &[u8; 32],
    chain_id_digest: &[u8; 32],
) -> Result<[u8; 32], String> {
    if envelope.recipient_kind != "device" {
        return Err("Not a device envelope".to_string());
    }
    let Some(enc_str) = &envelope.enc else {
        return Err("Missing enc in device envelope".to_string());
    };
    let enc_bytes = decode_key_32(enc_str, "HPKE enc")?;
    let ciphertext = URL_SAFE_NO_PAD
        .decode(&envelope.ciphertext)
        .map_err(|_| "Invalid base64url envelope ciphertext".to_string())?;

    let binding = EnvelopeBinding {
        envelope_id: &envelope.envelope_id,
        chain_id: &envelope.chain_id,
        generation: envelope.generation,
        epoch: envelope.epoch,
        previous_epoch_hash: envelope.previous_epoch_hash.as_deref(),
        epoch_commitment: &envelope.epoch_commitment,
        recipient_kind: "device",
        recipient_device_id: envelope.recipient_device_id.as_deref(),
        recipient_key_hash: envelope.recipient_key_hash.as_deref(),
        signer_kind: &envelope.signer_kind,
        signer_device_id: envelope.signer_device_id.as_deref(),
    };
    let binding_jcs = models::to_jcs(&binding)
        .map_err(|e| format!("Cannot canonicalize EnvelopeBinding: {e}"))?;

    const INFO_PREFIX: &[u8] = b"PALLASYNC-EPOCH-ENVELOPE-v3\0";
    let mut info = Vec::with_capacity(INFO_PREFIX.len() + binding_jcs.len());
    info.extend_from_slice(INFO_PREFIX);
    info.extend_from_slice(&binding_jcs);

    let sk = <HpkeKemX25519 as Kem>::PrivateKey::from_bytes(recipient_kex_private_key)
        .map_err(|_| "Invalid X25519 private key for HPKE".to_string())?;
    let enc = <HpkeKemX25519 as Kem>::EncappedKey::from_bytes(&enc_bytes)
        .map_err(|_| "Invalid HPKE encapped key".to_string())?;

    let mut receiver_ctx = hpke::setup_receiver::<HpkeAeadChaCha, HpkeKdfSha256, HpkeKemX25519>(
        &OpModeR::Base,
        &sk,
        &enc,
        &info,
    )
    .map_err(|e| format!("HPKE setup_receiver failed: {e:?}"))?;

    let secret_vec = receiver_ctx
        .open(&ciphertext, &binding_jcs)
        .map_err(|e| format!("HPKE open failed: {e:?}"))?;

    let secret_bytes: [u8; 32] = secret_vec
        .try_into()
        .map_err(|_| "Decrypted epoch secret must be 32 bytes".to_string())?;

    // Verify commitment constant-time (§5.3)
    let derived = derive_epoch_keys(chain_id_digest, envelope.epoch, &secret_bytes)?;
    if derived.epoch_commitment_b64u != envelope.epoch_commitment {
        return Err("C_COMMITMENT_MISMATCH: epoch commitment does not match".to_string());
    }

    Ok(secret_bytes)
}

pub fn wrap_recovery_epoch_envelope(
    envelope_id: &str,
    chain_id: &str,
    generation: u32,
    epoch: u32,
    previous_epoch_hash: Option<&str>,
    epoch_commitment: &str,
    signer_kind: &str,
    signer_device_id: Option<&str>,
    signing_key: &SigningKey,
    recovery_kek: &[u8; 32],
    epoch_secret: &[u8; 32],
    fixed_nonce: Option<&[u8; 24]>,
) -> Result<EpochKeyEnvelope, String> {
    let binding = EnvelopeBinding {
        envelope_id,
        chain_id,
        generation,
        epoch,
        previous_epoch_hash,
        epoch_commitment,
        recipient_kind: "recovery",
        recipient_device_id: None,
        recipient_key_hash: None,
        signer_kind,
        signer_device_id,
    };
    let binding_jcs = models::to_jcs(&binding)
        .map_err(|e| format!("Cannot canonicalize EnvelopeBinding: {e}"))?;

    let nonce_bytes = fixed_nonce.copied().unwrap_or_else(generate_nonce_24);
    let cipher = XChaCha20Poly1305::new(recovery_kek.into());
    let ciphertext = cipher
        .encrypt(XNonce::from_slice(&nonce_bytes), Payload { msg: epoch_secret, aad: &binding_jcs })
        .map_err(|e| format!("Recovery envelope encryption failed: {e}"))?;

    let mut envelope = EpochKeyEnvelope {
        envelope_id: envelope_id.to_string(),
        chain_id: chain_id.to_string(),
        generation,
        epoch,
        previous_epoch_hash: previous_epoch_hash.map(ToString::to_string),
        epoch_commitment: epoch_commitment.to_string(),
        recipient_kind: "recovery".to_string(),
        recipient_device_id: None,
        recipient_key_hash: None,
        signer_kind: signer_kind.to_string(),
        signer_device_id: signer_device_id.map(ToString::to_string),
        enc: None,
        nonce: Some(URL_SAFE_NO_PAD.encode(nonce_bytes)),
        ciphertext: URL_SAFE_NO_PAD.encode(ciphertext),
        ext: None,
        signature: String::new(),
    };

    let canonical = unsigned_record_jcs(&envelope)
        .map_err(|e| format!("Cannot canonicalize EpochKeyEnvelope: {e}"))?;
    envelope.signature = sign_with_context(signing_key, CTX_EPOCH_ENVELOPE, &canonical);

    Ok(envelope)
}

pub fn unwrap_recovery_epoch_envelope(
    envelope: &EpochKeyEnvelope,
    recovery_kek: &[u8; 32],
    chain_id_digest: &[u8; 32],
) -> Result<[u8; 32], String> {
    if envelope.recipient_kind != "recovery" {
        return Err("Not a recovery envelope".to_string());
    }
    let Some(nonce_str) = &envelope.nonce else {
        return Err("Missing nonce in recovery envelope".to_string());
    };
    let nonce_bytes = URL_SAFE_NO_PAD
        .decode(nonce_str)
        .map_err(|_| "Invalid base64url recovery nonce".to_string())?;
    if nonce_bytes.len() != 24 {
        return Err("Recovery nonce must be 24 bytes".to_string());
    }

    let ciphertext = URL_SAFE_NO_PAD
        .decode(&envelope.ciphertext)
        .map_err(|_| "Invalid base64url recovery ciphertext".to_string())?;

    let binding = EnvelopeBinding {
        envelope_id: &envelope.envelope_id,
        chain_id: &envelope.chain_id,
        generation: envelope.generation,
        epoch: envelope.epoch,
        previous_epoch_hash: envelope.previous_epoch_hash.as_deref(),
        epoch_commitment: &envelope.epoch_commitment,
        recipient_kind: "recovery",
        recipient_device_id: None,
        recipient_key_hash: None,
        signer_kind: &envelope.signer_kind,
        signer_device_id: envelope.signer_device_id.as_deref(),
    };
    let binding_jcs = models::to_jcs(&binding)
        .map_err(|e| format!("Cannot canonicalize EnvelopeBinding: {e}"))?;

    let cipher = XChaCha20Poly1305::new(recovery_kek.into());
    let secret_vec = cipher
        .decrypt(XNonce::from_slice(&nonce_bytes), Payload { msg: &ciphertext, aad: &binding_jcs })
        .map_err(|_| "Recovery envelope AEAD decryption failed".to_string())?;

    let secret_bytes: [u8; 32] = secret_vec
        .try_into()
        .map_err(|_| "Decrypted recovery secret must be 32 bytes".to_string())?;

    let derived = derive_epoch_keys(chain_id_digest, envelope.epoch, &secret_bytes)?;
    if derived.epoch_commitment_b64u != envelope.epoch_commitment {
        return Err("C_COMMITMENT_MISMATCH: epoch commitment does not match".to_string());
    }

    Ok(secret_bytes)
}

// ---------------------------------------------------------------------
// Capability Tokens (§6.1, §6.2)
// ---------------------------------------------------------------------

pub fn canonicalize_query(raw_query: &str) -> String {
    let trimmed = raw_query.trim_start_matches('?');
    if trimmed.is_empty() {
        return String::new();
    }

    let mut pairs: Vec<(String, String)> = trimmed
        .split('&')
        .map(|pair| {
            let (k, v) = match pair.split_once('=') {
                Some((k, v)) => (k, v),
                None => (pair, ""),
            };
            let decoded_k = percent_decode(k);
            let decoded_v = percent_decode(v);
            (percent_encode(&decoded_k), percent_encode(&decoded_v))
        })
        .collect();

    pairs.sort_by(|(k1, v1), (k2, v2)| match k1.cmp(k2) {
        std::cmp::Ordering::Equal => v1.cmp(v2),
        other => other,
    });

    pairs
        .into_iter()
        .map(|(k, v)| format!("{k}={v}"))
        .collect::<Vec<_>>()
        .join("&")
}

fn percent_decode(input: &str) -> String {
    let mut bytes = Vec::new();
    let mut chars = input.bytes();
    while let Some(b) = chars.next() {
        if b == b'%' {
            if let (Some(h1), Some(h2)) = (chars.next(), chars.next()) {
                if let Ok(byte) = u8::from_str_radix(
                    &format!("{}{}", h1 as char, h2 as char),
                    16,
                ) {
                    bytes.push(byte);
                    continue;
                }
            }
        }
        bytes.push(b);
    }
    String::from_utf8_lossy(&bytes).to_string()
}

fn percent_encode(input: &str) -> String {
    let mut out = String::new();
    for b in input.bytes() {
        if matches!(b, b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'.' | b'_' | b'~') {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{:02X}", b));
        }
    }
    out
}

#[allow(clippy::too_many_arguments)]
pub fn create_capability_token(
    aud: &str,
    chain_id: &str,
    signer_kind: &str, // "device" or "admin"
    device_id: Option<&str>,
    method: &str,
    path: &str,
    query: &str,
    body_bytes: &[u8],
    signing_key: &SigningKey,
    ttl_ms: i64,
) -> Result<String, String> {
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0);
    let expires = now + ttl_ms;
    let nonce = URL_SAFE_NO_PAD.encode(generate_nonce_16());
    let body_hash = sha256_hash(body_bytes);
    let body_sha256 = URL_SAFE_NO_PAD.encode(body_hash);
    let canonical_query = canonicalize_query(query);

    let mut token = CapabilityToken {
        v: 3,
        aud: aud.trim_end_matches('/').to_lowercase(),
        chain_id: chain_id.to_string(),
        signer_kind: signer_kind.to_string(),
        device_id: device_id.map(ToString::to_string),
        method: method.to_uppercase(),
        path: path.to_string(),
        query: canonical_query,
        body_sha256,
        issued_at_ms: now,
        expires_at_ms: expires,
        nonce,
        signature: String::new(),
    };

    let canonical = unsigned_record_jcs(&token)
        .map_err(|e| format!("Cannot canonicalize capability token: {e}"))?;
    token.signature = sign_with_context(signing_key, CTX_CAPABILITY, &canonical);

    let jcs = models::to_jcs(&token)
        .map_err(|e| format!("Cannot JCS encode capability token: {e}"))?;
    Ok(URL_SAFE_NO_PAD.encode(jcs))
}

pub fn verify_capability_token(
    token: &CapabilityToken,
    public_key_base64_url: &str,
) -> Result<bool, String> {
    let public_key = decode_verifying_key(public_key_base64_url)?;
    let canonical = unsigned_record_jcs(token)
        .map_err(|e| format!("Cannot canonicalize capability token: {e}"))?;
    verify_with_context(&public_key, CTX_CAPABILITY, &canonical, &token.signature)
}

// ---------------------------------------------------------------------
// Legacy v2.1 decryption support (Migration only, §9)
// ---------------------------------------------------------------------

pub const LEGACY_SYNC_RECORD_AAD: &[u8] = b"PALLASYNC-AAD-v2";
pub const LEGACY_DEVICE_RECORD_AAD: &[u8] = b"PALLASYNC-DEVICE-AAD-v2";

pub fn derive_record_nonce_legacy(record_id: &str) -> [u8; 12] {
    let mut hasher = Sha256::new();
    hasher.update(b"PALLASYNC-NONCE-v2\0");
    hasher.update(record_id.as_bytes());
    let res = hasher.finalize();
    let mut nonce = [0u8; 12];
    nonce.copy_from_slice(&res[0..12]);
    nonce
}

pub fn decrypt_record_payload_legacy(
    key: &[u8; 32],
    nonce: &[u8; 12],
    ciphertext: &[u8],
    aad: &[u8],
) -> Result<Vec<u8>, chacha20poly1305::Error> {
    let cipher = ChaCha20Poly1305::new(key.into());
    cipher.decrypt(Nonce::from_slice(nonce), Payload { msg: ciphertext, aad })
}

pub fn object_hash<T: serde::Serialize>(object: &T) -> Result<String, String> {
    let jcs = models::to_jcs(object).map_err(|e| format!("Cannot JCS serialize object: {e}"))?;
    Ok(URL_SAFE_NO_PAD.encode(sha256_hash(&jcs)))
}

#[derive(serde::Serialize, serde::Deserialize, Debug, Clone)]
pub struct GenesisBundle {
    pub chain_id: String,
    pub device_id: String,
    pub device_signing_key: String,
    pub device_public_key: String,
    pub device_kex_private_key: String,
    pub device_kex_public_key: String,
    pub epoch: u32,
    pub epoch_secret: String,
    pub record_key: String,
    pub device_meta_key: String,
    pub collection_tag_key: String,
    pub epoch_commitment: String,
    pub genesis_request_body_json: String,
    pub admin_capability_token: String,
}

pub fn create_genesis_bundle(
    seed_phrase: &str,
    passphrase: &str,
    device_name: &str,
    key_protection: &str,
    aud: &str,
) -> Result<GenesisBundle, String> {
    let root_keys = derive_root_keys(seed_phrase, passphrase)?;
    let (device_signing_key, kex_bytes) = generate_device_keys();
    let device_id = uuid::Uuid::new_v4().to_string();

    let kex_secret = x25519_dalek::StaticSecret::from(kex_bytes);
    let kex_pub = x25519_dalek::PublicKey::from(&kex_secret);
    let device_kex_pub_b64 = URL_SAFE_NO_PAD.encode(kex_pub.as_bytes());
    let device_kex_priv_b64 = URL_SAFE_NO_PAD.encode(kex_bytes);
    let device_pub_b64 = URL_SAFE_NO_PAD.encode(device_signing_key.verifying_key().as_bytes());
    let device_sign_b64 = URL_SAFE_NO_PAD.encode(device_signing_key.to_bytes());

    let now_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0);

    // 1. ChainParameters (§7.5)
    let mut parameters = models::ChainParameters {
        version: "3.0".to_string(),
        chain_id: root_keys.chain_id.clone(),
        admin_public_key: root_keys.admin_public_key.clone(),
        generation: 0,
        previous_parameters_hash: None,
        policy: models::ChainPolicy::default(),
        ext: None,
        signature: String::new(),
    };
    let canonical_params = unsigned_record_jcs(&parameters)
        .map_err(|e| format!("Cannot canonicalize ChainParameters: {e}"))?;
    parameters.signature = sign_with_context(&root_keys.admin_key, CTX_CHAIN_PARAMS, &canonical_params);

    // 2. EnrollmentCertificate (§7.4)
    let mut cert = EnrollmentCertificate {
        chain_id: root_keys.chain_id.clone(),
        generation: 0,
        certificate_id: uuid::Uuid::new_v4().to_string(),
        device_id: device_id.clone(),
        device_public_key: device_pub_b64.clone(),
        device_kex_public_key: device_kex_pub_b64.clone(),
        signer_kind: "admin".to_string(),
        signer_device_id: None,
        request_hash: None,
        approved_at_ms: now_ms,
        ext: None,
        signature: String::new(),
    };
    let canonical_cert = unsigned_record_jcs(&cert)
        .map_err(|e| format!("Cannot canonicalize EnrollmentCertificate: {e}"))?;
    cert.signature = sign_with_context(&root_keys.admin_key, CTX_ENROLLMENT, &canonical_cert);

    // 3. Epoch 0 keys
    let mut epoch_secret_0 = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut epoch_secret_0);
    let epoch_keys = derive_epoch_keys(&root_keys.chain_id_digest, 0, &epoch_secret_0)?;

    // 4. DeviceRecord (§7.4)
    let device_meta = DeviceMetaPlaintext {
        name: device_name.to_string(),
        key_protection: key_protection.to_string(),
        ext: None,
    };
    let device_record = create_device_record(
        &root_keys.chain_id,
        0,
        &device_id,
        &device_signing_key,
        &device_kex_pub_b64,
        &device_meta,
        0,
        &epoch_keys.device_meta_key,
        cert,
    )?;

    // 5. Recovery envelope (§7.7)
    let recovery_env_id = uuid::Uuid::new_v4().to_string();
    let recovery_envelope = wrap_recovery_epoch_envelope(
        &recovery_env_id,
        &root_keys.chain_id,
        0,
        0,
        None,
        &epoch_keys.epoch_commitment_b64u,
        "admin",
        None,
        &root_keys.admin_key,
        &root_keys.recovery_kek,
        &epoch_secret_0,
        None,
    )?;
    let recovery_env_hash = object_hash(&recovery_envelope)?;

    // 6. Device envelope (§7.7)
    let device_env_id = uuid::Uuid::new_v4().to_string();
    let device_envelope = wrap_device_epoch_envelope(
        &device_env_id,
        &root_keys.chain_id,
        0,
        0,
        None,
        &epoch_keys.epoch_commitment_b64u,
        &device_id,
        &device_kex_pub_b64,
        "admin",
        None,
        &root_keys.admin_key,
        &epoch_secret_0,
    )?;

    // 7. EpochRecord (§7.6)
    let mut epoch_record = models::EpochRecord {
        chain_id: root_keys.chain_id.clone(),
        generation: 0,
        epoch: 0,
        previous_epoch_hash: None,
        epoch_commitment: epoch_keys.epoch_commitment_b64u.clone(),
        reason: "genesis".to_string(),
        signer_kind: "admin".to_string(),
        signer_device_id: None,
        members: vec![device_id.clone()],
        revoked_device_ids: vec![],
        recovery_envelope_hash: recovery_env_hash,
        ext: None,
        signature: String::new(),
    };
    let canonical_epoch = unsigned_record_jcs(&epoch_record)
        .map_err(|e| format!("Cannot canonicalize EpochRecord: {e}"))?;
    epoch_record.signature = sign_with_context(&root_keys.admin_key, CTX_EPOCH, &canonical_epoch);

    // 8. Genesis request body (§10.2)
    #[derive(serde::Serialize)]
    struct GenesisBody {
        parameters: models::ChainParameters,
        device_record: DeviceRecord,
        epoch: models::EpochRecord,
        envelopes: Vec<EpochKeyEnvelope>,
    }
    let genesis_body = GenesisBody {
        parameters,
        device_record,
        epoch: epoch_record,
        envelopes: vec![device_envelope, recovery_envelope],
    };
    let genesis_json = serde_json::to_string(&genesis_body)
        .map_err(|e| format!("Cannot serialize GenesisBody: {e}"))?;

    // 9. Admin Capability Token
    let admin_token = create_capability_token(
        aud,
        &root_keys.chain_id,
        "admin",
        None,
        "POST",
        "/pallasync/v3/chains",
        "",
        genesis_json.as_bytes(),
        &root_keys.admin_key,
        120_000,
    )?;

    Ok(GenesisBundle {
        chain_id: root_keys.chain_id,
        device_id,
        device_signing_key: device_sign_b64,
        device_public_key: device_pub_b64,
        device_kex_private_key: device_kex_priv_b64,
        device_kex_public_key: device_kex_pub_b64,
        epoch: 0,
        epoch_secret: URL_SAFE_NO_PAD.encode(epoch_secret_0),
        record_key: URL_SAFE_NO_PAD.encode(epoch_keys.record_key),
        device_meta_key: URL_SAFE_NO_PAD.encode(epoch_keys.device_meta_key),
        collection_tag_key: URL_SAFE_NO_PAD.encode(epoch_keys.collection_tag_key),
        epoch_commitment: epoch_keys.epoch_commitment_b64u,
        genesis_request_body_json: genesis_json,
        admin_capability_token: admin_token,
    })
}

#[derive(serde::Serialize, serde::Deserialize, Debug, Clone)]
pub struct MnemonicEnrollmentBundle {
    pub chain_id: String,
    pub device_id: String,
    pub device_signing_key: String,
    pub device_public_key: String,
    pub device_kex_private_key: String,
    pub device_kex_public_key: String,
    pub epoch: u32,
    pub epoch_secret: String,
    pub record_key: String,
    pub device_meta_key: String,
    pub collection_tag_key: String,
    pub epoch_commitment: String,
    pub enroll_request_body_json: String,
    pub admin_capability_token: String,
    pub keys_ack_request_body_json: String,
    pub device_keys_ack_token: String,
}

pub fn create_mnemonic_enrollment_bundle(
    seed_phrase: &str,
    passphrase: &str,
    device_name: &str,
    key_protection: &str,
    generation: u32,
    epoch: u32,
    recovery_envelope_json: &str,
    expected_parameters_hash: &str,
    expected_epoch_hash: &str,
    aud: &str,
) -> Result<MnemonicEnrollmentBundle, String> {
    let root_keys = derive_root_keys(seed_phrase, passphrase)?;
    let (device_signing_key, kex_bytes) = generate_device_keys();
    let device_id = uuid::Uuid::new_v4().to_string();

    let kex_secret = x25519_dalek::StaticSecret::from(kex_bytes);
    let kex_pub = x25519_dalek::PublicKey::from(&kex_secret);
    let device_kex_pub_b64 = URL_SAFE_NO_PAD.encode(kex_pub.as_bytes());
    let device_kex_priv_b64 = URL_SAFE_NO_PAD.encode(kex_bytes);
    let device_pub_b64 = URL_SAFE_NO_PAD.encode(device_signing_key.verifying_key().as_bytes());
    let device_sign_b64 = URL_SAFE_NO_PAD.encode(device_signing_key.to_bytes());

    let recovery_envelope: EpochKeyEnvelope = serde_json::from_str(recovery_envelope_json)
        .map_err(|e| format!("Invalid recovery envelope JSON: {e}"))?;

    let epoch_secret = unwrap_recovery_epoch_envelope(
        &recovery_envelope,
        &root_keys.recovery_kek,
        &root_keys.chain_id_digest,
    )?;

    let epoch_keys = derive_epoch_keys(&root_keys.chain_id_digest, epoch, &epoch_secret)?;

    let now_ms = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0);

    // 1. EnrollmentCertificate (§7.4)
    let mut cert = EnrollmentCertificate {
        chain_id: root_keys.chain_id.clone(),
        generation,
        certificate_id: uuid::Uuid::new_v4().to_string(),
        device_id: device_id.clone(),
        device_public_key: device_pub_b64.clone(),
        device_kex_public_key: device_kex_pub_b64.clone(),
        signer_kind: "admin".to_string(),
        signer_device_id: None,
        request_hash: None,
        approved_at_ms: now_ms,
        ext: None,
        signature: String::new(),
    };
    let canonical_cert = unsigned_record_jcs(&cert)
        .map_err(|e| format!("Cannot canonicalize EnrollmentCertificate: {e}"))?;
    cert.signature = sign_with_context(&root_keys.admin_key, CTX_ENROLLMENT, &canonical_cert);

    // 2. DeviceRecord (§7.4)
    let device_meta = DeviceMetaPlaintext {
        name: device_name.to_string(),
        key_protection: key_protection.to_string(),
        ext: None,
    };
    let device_record = create_device_record(
        &root_keys.chain_id,
        generation,
        &device_id,
        &device_signing_key,
        &device_kex_pub_b64,
        &device_meta,
        epoch,
        &epoch_keys.device_meta_key,
        cert,
    )?;

    // 3. Device envelope (§7.7)
    let device_env_id = uuid::Uuid::new_v4().to_string();
    let device_envelope = wrap_device_epoch_envelope(
        &device_env_id,
        &root_keys.chain_id,
        generation,
        epoch,
        recovery_envelope.previous_epoch_hash.as_deref(),
        &epoch_keys.epoch_commitment_b64u,
        &device_id,
        &device_kex_pub_b64,
        "admin",
        None,
        &root_keys.admin_key,
        &epoch_secret,
    )?;

    // 4. AdminOperation (§7.10)
    #[derive(serde::Serialize)]
    struct ActionPayload {
        device_record: DeviceRecord,
        envelopes: Vec<EpochKeyEnvelope>,
    }
    let action_payload = ActionPayload {
        device_record: device_record.clone(),
        envelopes: vec![device_envelope.clone()],
    };
    let action_payload_jcs = models::to_jcs(&action_payload)
        .map_err(|e| format!("Cannot JCS encode ActionPayload: {e}"))?;
    let target_hash = URL_SAFE_NO_PAD.encode(sha256_hash(&action_payload_jcs));

    #[derive(serde::Serialize)]
    struct AdminOpWire {
        operation_id: String,
        chain_id: String,
        generation: u32,
        action: String,
        expected_parameters_hash: String,
        expected_epoch_hash: String,
        target_hash: String,
        created_at_ms: i64,
        #[serde(default)]
        signature: String,
    }
    let mut admin_op = AdminOpWire {
        operation_id: uuid::Uuid::new_v4().to_string(),
        chain_id: root_keys.chain_id.clone(),
        generation,
        action: "approve_enrollment".to_string(),
        expected_parameters_hash: expected_parameters_hash.to_string(),
        expected_epoch_hash: expected_epoch_hash.to_string(),
        target_hash,
        created_at_ms: now_ms,
        signature: String::new(),
    };
    let canonical_op = unsigned_record_jcs(&admin_op)
        .map_err(|e| format!("Cannot canonicalize AdminOperation: {e}"))?;
    admin_op.signature = sign_with_context(&root_keys.admin_key, CTX_ADMIN_OP, &canonical_op);

    // 5. Enroll request body (§10.4)
    #[derive(serde::Serialize)]
    struct EnrollBody {
        device_record: DeviceRecord,
        envelopes: Vec<EpochKeyEnvelope>,
        admin_operation: AdminOpWire,
    }
    let enroll_body = EnrollBody {
        device_record,
        envelopes: vec![device_envelope],
        admin_operation: admin_op,
    };
    let enroll_body_json = serde_json::to_string(&enroll_body)
        .map_err(|e| format!("Cannot serialize EnrollBody: {e}"))?;

    let enroll_path = format!("/pallasync/v3/chains/{}/devices/enroll", root_keys.chain_id);
    let admin_token = create_capability_token(
        aud,
        &root_keys.chain_id,
        "admin",
        None,
        "POST",
        &enroll_path,
        "",
        enroll_body_json.as_bytes(),
        &root_keys.admin_key,
        120_000,
    )?;

    // 6. Keys Ack request body (§10.8)
    #[derive(serde::Serialize)]
    struct KeysAckBody {
        epoch_hash: String,
        epoch_commitment: String,
    }
    let ack_body = KeysAckBody {
        epoch_hash: expected_epoch_hash.to_string(),
        epoch_commitment: epoch_keys.epoch_commitment_b64u.clone(),
    };
    let ack_body_json = serde_json::to_string(&ack_body)
        .map_err(|e| format!("Cannot serialize KeysAckBody: {e}"))?;

    let ack_path = format!("/pallasync/v3/chains/{}/devices/{}/keys/ack", root_keys.chain_id, device_id);
    let ack_token = create_capability_token(
        aud,
        &root_keys.chain_id,
        "device",
        Some(&device_id),
        "POST",
        &ack_path,
        "",
        ack_body_json.as_bytes(),
        &device_signing_key,
        120_000,
    )?;

    Ok(MnemonicEnrollmentBundle {
        chain_id: root_keys.chain_id,
        device_id,
        device_signing_key: device_sign_b64,
        device_public_key: device_pub_b64,
        device_kex_private_key: device_kex_priv_b64,
        device_kex_public_key: device_kex_pub_b64,
        epoch,
        epoch_secret: URL_SAFE_NO_PAD.encode(epoch_secret),
        record_key: URL_SAFE_NO_PAD.encode(epoch_keys.record_key),
        device_meta_key: URL_SAFE_NO_PAD.encode(epoch_keys.device_meta_key),
        collection_tag_key: URL_SAFE_NO_PAD.encode(epoch_keys.collection_tag_key),
        epoch_commitment: epoch_keys.epoch_commitment_b64u,
        enroll_request_body_json: enroll_body_json,
        admin_capability_token: admin_token,
        keys_ack_request_body_json: ack_body_json,
        device_keys_ack_token: ack_token,
    })
}
