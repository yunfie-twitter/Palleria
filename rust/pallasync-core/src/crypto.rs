use crate::models::{
    self, CapabilityToken, DeviceRecord, PROTOCOL_VERSION_2_1, RecordAAD, SyncRecord,
};
use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use bip39::{Language, Mnemonic};
use chacha20poly1305::{
    ChaCha20Poly1305, KeyInit, Nonce, XChaCha20Poly1305, XNonce,
    aead::{Aead as AeadTrait, Payload},
};
use ed25519_dalek::{Signature, Signer, SigningKey, VerifyingKey};
use hkdf::Hkdf;
use hmac::Hmac;
use rand::RngCore;
use rand_core::OsRng;
use serde::Serialize;
use sha2::{Digest, Sha256};

pub type HmacSha256 = Hmac<Sha256>;

pub const CTX_SYNC_RECORD: &[u8] = b"PALLASYNC-SYNC-RECORD-v2.1\0";
pub const CTX_DEVICE_RECORD: &[u8] = b"PALLASYNC-DEVICE-RECORD-v2.1\0";
pub const CTX_CAPABILITY: &[u8] = b"PALLASYNC-CAPABILITY-v2.1\0";
pub const CTX_ADMIN_OP: &[u8] = b"PALLASYNC-ADMIN-OP-v2.1\0";
pub const CTX_INVITATION: &[u8] = b"PALLASYNC-INVITATION-v2.1\0";

pub const LEGACY_SYNC_RECORD_AAD: &[u8] = b"PALLASYNC-AAD-v2";
pub const LEGACY_DEVICE_RECORD_AAD: &[u8] = b"PALLASYNC-DEVICE-AAD-v2";

pub fn generate_seed_phrase() -> String {
    let mut entropy = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut entropy);
    let mnemonic = Mnemonic::from_entropy(&entropy).unwrap();
    mnemonic.to_string()
}

pub fn generate_salt() -> [u8; 32] {
    let mut salt = [0u8; 32];
    rand::thread_rng().fill_bytes(&mut salt);
    salt
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

pub struct DerivedKeys {
    pub chain_id: String,   // Base64Url
    pub chain_salt: String, // Base64Url
    pub epoch_key: [u8; 32],
    pub record_key: [u8; 32],
    pub device_name_key: [u8; 32],
    pub invite_key: [u8; 32],
    pub admin_key: SigningKey,
    pub encryption_key: [u8; 32], // alias for record_key
    pub signing_key: SigningKey,  // device signing key or admin key
}

pub fn derive_chain_id(salt: &[u8], root_seed: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(b"PALLASYNC-CHAIN-ID-v2.1\0");
    hasher.update(salt);
    hasher.update(root_seed);
    let digest = hasher.finalize();
    URL_SAFE_NO_PAD.encode(digest)
}

pub fn derive_keys_with_salt(
    seed_phrase: &str,
    passphrase: &str,
    salt: &[u8; 32],
) -> Result<DerivedKeys, String> {
    let mnemonic =
        Mnemonic::parse_in(Language::English, seed_phrase).map_err(|_| "Invalid seed phrase")?;
    let root_seed = mnemonic.to_seed(passphrase);

    let chain_id = derive_chain_id(salt, &root_seed);

    let hk = Hkdf::<Sha256>::new(Some(salt), &root_seed);

    const EPOCH_CONTEXT: &[u8] = b"PALLASYNC-v2.1\0epoch\0";
    let mut epoch_info = Vec::with_capacity(EPOCH_CONTEXT.len() + 4);
    epoch_info.extend_from_slice(EPOCH_CONTEXT);
    epoch_info.extend_from_slice(&0u32.to_be_bytes());
    let mut epoch_key = [0u8; 32];
    hk.expand(&epoch_info, &mut epoch_key)
        .map_err(|_| "HKDF fail: epoch_key")?;

    let hk_epoch = Hkdf::<Sha256>::new(None, &epoch_key);
    let mut record_key = [0u8; 32];
    hk_epoch
        .expand(b"PALLASYNC-v2.1\0record\0", &mut record_key)
        .map_err(|_| "HKDF fail: record_key")?;

    let mut device_name_key = [0u8; 32];
    hk_epoch
        .expand(b"PALLASYNC-v2.1\0device-name\0", &mut device_name_key)
        .map_err(|_| "HKDF fail: device_name_key")?;

    let mut invite_key = [0u8; 32];
    hk.expand(b"PALLASYNC-v2.1\0invite-auth\0", &mut invite_key)
        .map_err(|_| "HKDF fail: invite_key")?;

    let mut admin_seed = [0u8; 32];
    hk.expand(b"PALLASYNC-v2.1\0admin-ed25519\0", &mut admin_seed)
        .map_err(|_| "HKDF fail: admin_seed")?;

    let admin_key = SigningKey::from_bytes(&admin_seed);
    let signing_key = admin_key.clone();

    Ok(DerivedKeys {
        chain_id,
        chain_salt: URL_SAFE_NO_PAD.encode(salt),
        epoch_key,
        record_key,
        device_name_key,
        invite_key,
        admin_key,
        encryption_key: record_key,
        signing_key,
    })
}

pub fn derive_keys_from_seed(seed_phrase: &str) -> Result<DerivedKeys, String> {
    let mut hasher = Sha256::new();
    hasher.update(b"PALLASYNC-DEFAULT-SALT-v2.1\0");
    hasher.update(seed_phrase.as_bytes());
    let salt: [u8; 32] = hasher.finalize().into();
    derive_keys_with_salt(seed_phrase, "", &salt)
}

pub fn generate_ed25519_keypair() -> SigningKey {
    let mut csprng = OsRng;
    SigningKey::generate(&mut csprng)
}

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
    let signature = decode_signature(signature_base64_url)?;
    let mut msg = Vec::with_capacity(context.len() + canonical_jcs.len());
    msg.extend_from_slice(context);
    msg.extend_from_slice(canonical_jcs);

    if public_key.verify_strict(&msg, &signature).is_ok() {
        return Ok(true);
    }

    // 2.0 fallback: SHA-256 pre-hash verification without context
    let hash = sha256_hash(canonical_jcs);
    if public_key.verify_strict(&hash, &signature).is_ok() {
        return Ok(true);
    }
    // Also try direct without context
    if public_key.verify_strict(canonical_jcs, &signature).is_ok() {
        return Ok(true);
    }

    Ok(false)
}

pub fn sha256_hash(data: &[u8]) -> [u8; 32] {
    Sha256::digest(data).into()
}

pub fn derive_record_nonce_legacy(record_id: &str) -> [u8; 12] {
    let mut hasher = Sha256::new();
    hasher.update(b"PALLASYNC-NONCE-v2\0");
    hasher.update(record_id.as_bytes());
    let res = hasher.finalize();
    let mut nonce = [0u8; 12];
    nonce.copy_from_slice(&res[0..12]);
    nonce
}

pub fn encrypt_record_payload_xchacha(
    key: &[u8; 32],
    nonce: &[u8; 24],
    payload: &[u8],
    aad: &[u8],
) -> Vec<u8> {
    let cipher = XChaCha20Poly1305::new(key.into());
    cipher
        .encrypt(XNonce::from_slice(nonce), Payload { msg: payload, aad })
        .expect("XChaCha20Poly1305 encryption failure")
}

pub fn decrypt_record_payload_xchacha(
    key: &[u8; 32],
    nonce: &[u8; 24],
    ciphertext: &[u8],
    aad: &[u8],
) -> Result<Vec<u8>, chacha20poly1305::Error> {
    let cipher = XChaCha20Poly1305::new(key.into());
    cipher.decrypt(
        XNonce::from_slice(nonce),
        Payload {
            msg: ciphertext,
            aad,
        },
    )
}

pub fn encrypt_record_payload_legacy(
    key: &[u8; 32],
    nonce: &[u8; 12],
    payload: &[u8],
    aad: &[u8],
) -> Vec<u8> {
    let cipher = ChaCha20Poly1305::new(key.into());
    cipher
        .encrypt(Nonce::from_slice(nonce), Payload { msg: payload, aad })
        .expect("ChaCha20Poly1305 encryption failure")
}

pub fn decrypt_record_payload_legacy(
    key: &[u8; 32],
    nonce: &[u8; 12],
    ciphertext: &[u8],
    aad: &[u8],
) -> Result<Vec<u8>, chacha20poly1305::Error> {
    let cipher = ChaCha20Poly1305::new(key.into());
    cipher.decrypt(
        Nonce::from_slice(nonce),
        Payload {
            msg: ciphertext,
            aad,
        },
    )
}

fn decode_fixed<const N: usize>(encoded: &str, label: &str) -> Result<[u8; N], String> {
    let decoded = URL_SAFE_NO_PAD
        .decode(encoded)
        .map_err(|_| format!("Invalid base64url {label}"))?;
    decoded
        .try_into()
        .map_err(|_| format!("{label} must be {N} bytes"))
}

pub fn decode_encryption_key(encoded: &str) -> Result<[u8; 32], String> {
    decode_fixed(encoded, "encryption key")
}

pub fn decode_signing_key(encoded: &str) -> Result<SigningKey, String> {
    Ok(SigningKey::from_bytes(&decode_fixed(
        encoded,
        "signing key",
    )?))
}

pub fn decode_verifying_key(encoded: &str) -> Result<VerifyingKey, String> {
    VerifyingKey::from_bytes(&decode_fixed(encoded, "public key")?)
        .map_err(|_| "Invalid Ed25519 public key".to_string())
}

pub fn decode_signature(encoded: &str) -> Result<Signature, String> {
    Ok(Signature::from_bytes(&decode_fixed(encoded, "signature")?))
}

pub fn sign_sync_record(record: &mut SyncRecord, signing_key: &SigningKey) -> Result<(), String> {
    let canonical = models::sync_record_signing_bytes(record)
        .map_err(|error| format!("Cannot canonicalize sync record: {error}"))?;
    record.signature = sign_with_context(signing_key, CTX_SYNC_RECORD, &canonical);
    Ok(())
}

pub fn verify_sync_record(
    record: &SyncRecord,
    public_key_base64_url: &str,
) -> Result<bool, String> {
    let public_key = decode_verifying_key(public_key_base64_url)?;
    let canonical = models::sync_record_signing_bytes(record)
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

pub fn sign_device_record(
    record: &mut DeviceRecord,
    signing_key: &SigningKey,
) -> Result<(), String> {
    let canonical = models::device_record_signing_bytes(record)
        .map_err(|error| format!("Cannot canonicalize device record: {error}"))?;
    record.signature = sign_with_context(signing_key, CTX_DEVICE_RECORD, &canonical);
    Ok(())
}

pub fn verify_device_record(record: &DeviceRecord) -> Result<bool, String> {
    let public_key = decode_verifying_key(&record.device_public_key)?;
    let canonical = models::device_record_signing_bytes(record)
        .map_err(|error| format!("Cannot canonicalize device record: {error}"))?;
    verify_with_context(
        &public_key,
        CTX_DEVICE_RECORD,
        &canonical,
        &record.signature,
    )
}

pub fn verify_device_record_json(record_json: &str) -> Result<bool, String> {
    let record = serde_json::from_str::<DeviceRecord>(record_json)
        .map_err(|error| format!("Invalid device record JSON: {error}"))?;
    verify_device_record(&record)
}

#[allow(clippy::too_many_arguments)]
pub fn create_sync_record_at(
    chain_id: &str,
    record_id: &str,
    collection_name: &str,
    action: &str,
    payload: &[u8],
    device_id: &str,
    encryption_key: &[u8; 32],
    signing_key: &SigningKey,
    lamport: i64,
    created_at_ms: i64,
) -> Result<SyncRecord, String> {
    let nonce_bytes = generate_nonce_24();
    let payload_nonce = URL_SAFE_NO_PAD.encode(nonce_bytes);

    let aad_obj = RecordAAD {
        protocol_version: PROTOCOL_VERSION_2_1,
        chain_id,
        record_id,
        epoch: 0,
        collection_name,
        action,
        device_id,
        lamport,
        created_at_ms,
    };
    let aad =
        models::to_jcs(&aad_obj).map_err(|e| format!("Cannot canonicalize RecordAAD: {e}"))?;

    let encrypted_payload = URL_SAFE_NO_PAD.encode(encrypt_record_payload_xchacha(
        encryption_key,
        &nonce_bytes,
        payload,
        &aad,
    ));

    let mut record = SyncRecord {
        protocol_version: PROTOCOL_VERSION_2_1.to_string(),
        chain_id: chain_id.to_string(),
        record_id: record_id.to_string(),
        epoch: 0,
        collection_name: collection_name.to_string(),
        action: action.to_string(),
        encrypted_payload,
        payload_nonce,
        device_id: device_id.to_string(),
        lamport,
        created_at_ms,
        signature: String::new(),
    };
    sign_sync_record(&mut record, signing_key)?;
    Ok(record)
}

pub fn decrypt_sync_record(
    record: &SyncRecord,
    encryption_key: &[u8; 32],
) -> Result<Vec<u8>, String> {
    let ciphertext = URL_SAFE_NO_PAD
        .decode(&record.encrypted_payload)
        .map_err(|_| "Invalid base64url encrypted payload".to_string())?;

    // Try 2.1 XChaCha20-Poly1305 if payload_nonce is 24 bytes
    if !record.payload_nonce.is_empty() {
        if let Ok(nonce_bytes) = URL_SAFE_NO_PAD.decode(&record.payload_nonce) {
            if nonce_bytes.len() == 24 {
                let nonce_24: [u8; 24] = nonce_bytes.try_into().unwrap();
                let aad_obj = RecordAAD {
                    protocol_version: &record.protocol_version,
                    chain_id: &record.chain_id,
                    record_id: &record.record_id,
                    epoch: record.epoch,
                    collection_name: &record.collection_name,
                    action: &record.action,
                    device_id: &record.device_id,
                    lamport: record.lamport,
                    created_at_ms: record.created_at_ms,
                };
                if let Ok(aad) = models::to_jcs(&aad_obj) {
                    if let Ok(plaintext) =
                        decrypt_record_payload_xchacha(encryption_key, &nonce_24, &ciphertext, &aad)
                    {
                        return Ok(plaintext);
                    }
                }
            }
        }
    }

    // 2.0 fallback: ChaCha20-Poly1305 with deterministic nonce and legacy AAD
    let legacy_nonce = derive_record_nonce_legacy(&record.record_id);
    if let Ok(plaintext) = decrypt_record_payload_legacy(
        encryption_key,
        &legacy_nonce,
        &ciphertext,
        LEGACY_SYNC_RECORD_AAD,
    ) {
        return Ok(plaintext);
    }

    Err("Cannot decrypt sync record".to_string())
}

#[derive(Serialize)]
struct DeviceNameAAD<'a> {
    pub r#type: &'static str,
    pub chain_id: &'a str,
    pub device_id: &'a str,
    pub device_public_key: &'a str,
    pub updated_at_ms: i64,
}

pub fn create_device_record_at(
    chain_id: &str,
    device_id: &str,
    device_name: &[u8],
    encryption_key: &[u8; 32],
    signing_key: &SigningKey,
    created_at_ms: i64,
) -> Result<DeviceRecord, String> {
    let nonce_bytes = generate_nonce_24();
    let device_name_nonce = URL_SAFE_NO_PAD.encode(nonce_bytes);
    let public_key_b64 = URL_SAFE_NO_PAD.encode(signing_key.verifying_key().as_bytes());

    let aad_obj = DeviceNameAAD {
        r#type: "PALLASYNC-DEVICE-NAME-v2.1",
        chain_id,
        device_id,
        device_public_key: &public_key_b64,
        updated_at_ms: created_at_ms,
    };
    let aad = models::to_jcs(&aad_obj).map_err(|e| format!("AAD JCS failure: {e}"))?;

    let encrypted_device_name = URL_SAFE_NO_PAD.encode(encrypt_record_payload_xchacha(
        encryption_key,
        &nonce_bytes,
        device_name,
        &aad,
    ));

    let mut record = DeviceRecord {
        protocol_version: PROTOCOL_VERSION_2_1.to_string(),
        chain_id: chain_id.to_string(),
        device_id: device_id.to_string(),
        device_public_key: public_key_b64,
        encrypted_device_name,
        device_name_nonce,
        status: "active".to_string(),
        created_at_ms,
        updated_at_ms: created_at_ms,
        signature: String::new(),
    };
    sign_device_record(&mut record, signing_key)?;
    Ok(record)
}

pub fn decrypt_device_name(
    encrypted_device_name: &str,
    device_id: &str,
    encryption_key: &[u8; 32],
) -> Result<Vec<u8>, String> {
    let ciphertext = URL_SAFE_NO_PAD
        .decode(encrypted_device_name)
        .map_err(|_| "Invalid base64url encrypted device name".to_string())?;

    // Try legacy 2.0 ChaCha20Poly1305 with deterministic nonce
    let legacy_nonce = derive_record_nonce_legacy(device_id);
    if let Ok(plaintext) = decrypt_record_payload_legacy(
        encryption_key,
        &legacy_nonce,
        &ciphertext,
        LEGACY_DEVICE_RECORD_AAD,
    ) {
        return Ok(plaintext);
    }

    Err("Cannot decrypt device name".to_string())
}

pub fn decrypt_device_record_v21(
    record: &DeviceRecord,
    encryption_key: &[u8; 32],
) -> Result<Vec<u8>, String> {
    let ciphertext = URL_SAFE_NO_PAD
        .decode(&record.encrypted_device_name)
        .map_err(|_| "Invalid base64url encrypted device name".to_string())?;

    if !record.device_name_nonce.is_empty() {
        if let Ok(nonce_bytes) = URL_SAFE_NO_PAD.decode(&record.device_name_nonce) {
            if nonce_bytes.len() == 24 {
                let nonce_24: [u8; 24] = nonce_bytes.try_into().unwrap();
                let aad_obj = DeviceNameAAD {
                    r#type: "PALLASYNC-DEVICE-NAME-v2.1",
                    chain_id: &record.chain_id,
                    device_id: &record.device_id,
                    device_public_key: &record.device_public_key,
                    updated_at_ms: record.updated_at_ms,
                };
                if let Ok(aad) = models::to_jcs(&aad_obj) {
                    if let Ok(plaintext) =
                        decrypt_record_payload_xchacha(encryption_key, &nonce_24, &ciphertext, &aad)
                    {
                        return Ok(plaintext);
                    }
                }
            }
        }
    }

    // Fallback to legacy decryption
    decrypt_device_name(
        &record.encrypted_device_name,
        &record.device_id,
        encryption_key,
    )
}

pub fn create_capability_token(
    chain_id: &str,
    device_id: &str,
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

    let mut token = CapabilityToken {
        v: 1,
        chain_id: chain_id.to_string(),
        device_id: device_id.to_string(),
        method: method.to_uppercase(),
        path: path.to_string(),
        query: query.to_string(),
        body_sha256,
        issued_at_ms: now,
        expires_at_ms: expires,
        nonce,
        signature: String::new(),
    };

    let canonical = models::capability_token_signing_bytes(&token)
        .map_err(|e| format!("Cannot canonicalize capability token: {e}"))?;
    token.signature = sign_with_context(signing_key, CTX_CAPABILITY, &canonical);

    let jcs =
        models::to_jcs(&token).map_err(|e| format!("Cannot JCS encode capability token: {e}"))?;
    Ok(URL_SAFE_NO_PAD.encode(jcs))
}
