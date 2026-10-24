//! Local protected artifacts only (§§5.6/7.11). Nothing here is a relay payload.
use crate::{
    client_error::{ClientError, Result},
    crypto::{self, RootKeys},
    public_wire::{a, fields, n, s},
    schema::extension,
    shamir::{self, Piece},
    strict::{jcs, verify::*},
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use chacha20poly1305::{
    KeyInit, XChaCha20Poly1305, XNonce,
    aead::{Aead, Payload},
};
use ed25519_dalek::{Signer, SigningKey};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::collections::BTreeSet;
use subtle::ConstantTimeEq;
use unicode_normalization::UnicodeNormalization;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

pub const CTX_SET: &[u8] = b"PALLASYNC-RECOVERY-SET-v3\0";
pub const CTX_STATUS: &[u8] = b"PALLASYNC-RECOVERY-STATUS-v3\0";
pub const CTX_SHARE: &[u8] = b"PALLASYNC-RECOVERY-SHARE-v3\0";
pub const MAX_PACKAGE: usize = 4_194_304;
fn invalid() -> ClientError {
    ClientError("C_RECOVERY_SHARE_INVALID")
}
fn check(ok: bool) -> Result {
    if ok { Ok(()) } else { Err(invalid()) }
}
pub fn canonical(v: &Value) -> Result<Vec<u8>> {
    jcs::canonicalize_json(v).map_err(|_| invalid())
}
pub fn parse(bytes: &[u8]) -> Result<Value> {
    check(bytes.len() <= MAX_PACKAGE)?;
    jcs::parse(bytes).map_err(|_| invalid())
}
fn shape(v: &Value, required: &[(&str, &str)], signed: bool) -> Result {
    let mut required = required.to_vec();
    if signed {
        required.push(("signature", "b64"));
    }
    fields(v, &required, &[("ext", "object")]).map_err(|_| invalid())?;
    if let Some(e) = v.get("ext") {
        extension(e).map_err(|_| invalid())?;
    }
    Ok(())
}
pub fn sign(v: &mut Value, key: &SigningKey, ctx: &[u8]) -> Result {
    let bytes = signing_bytes(v, ctx, "signature");
    let sig = URL_SAFE_NO_PAD.encode(key.sign(&bytes).to_bytes());
    check(
        verify_signature(
            &URL_SAFE_NO_PAD.encode(key.verifying_key().as_bytes()),
            &sig,
            &bytes,
        )
        .unwrap_or(false),
    )?;
    v["signature"] = json!(sig);
    Ok(())
}
pub fn verify(v: &Value, key: &str, ctx: &[u8]) -> Result {
    check(verify_signed_json(key, s(v, "signature"), v, ctx).unwrap_or(false))
}
fn profile(v: &Value) -> Result {
    shamir::parameters(n(v, "threshold") as u16, n(v, "total_shares") as u16)
        .map_err(|_| invalid())?;
    check(n(v, "threshold") <= 255 && n(v, "total_shares") <= 255)
}
pub fn validate_blob(v: &Value) -> Result {
    fields(
        v,
        &[
            ("type", "PALLASYNC-RECOVERY-BLOB-v3"),
            ("version", "3.0"),
            ("chain_id", "b32"),
            ("generation", "u32"),
            ("recovery_set_id", "uuid"),
            ("threshold", "int"),
            ("total_shares", "int"),
            ("recovery_secret_fingerprint", "b32"),
            ("created_at_ms", "int"),
            ("blob_nonce", "b24"),
            ("encrypted_blob", "text"),
        ],
        &[],
    )
    .map_err(|_| invalid())?;
    profile(v)?;
    let c = decode(s(v, "encrypted_blob"), None).map_err(|_| invalid())?;
    check(c.len() >= 16 && crypto::SIZE_BUCKETS.contains(&(c.len() - 16)))
}
pub fn validate_manifest(v: &Value, admin: &str) -> Result {
    shape(
        v,
        &[
            ("type", "PALLASYNC-RECOVERY-SET-v3"),
            ("version", "3.0"),
            ("profile", "shamir-gf256-aes/1"),
            ("chain_id", "b32"),
            ("generation", "u32"),
            ("recovery_set_id", "uuid"),
            ("threshold", "int"),
            ("total_shares", "int"),
            ("recovery_secret_fingerprint", "b32"),
            ("created_at_ms", "int"),
            ("admin_public_key", "b32"),
            ("recovery_blob_hash", "b32"),
            ("holders", "array"),
        ],
        true,
    )?;
    profile(v)?;
    check(
        s(v, "admin_public_key") == admin && a(v, "holders").len() == n(v, "total_shares") as usize,
    )?;
    let mut instances = BTreeSet::new();
    let mut shares = BTreeSet::new();
    let mut devices = BTreeSet::new();
    for (i, h) in a(v, "holders").iter().enumerate() {
        fields(
            h,
            &[
                ("recovery_share_id", "uuid"),
                ("share_index", "int"),
                ("holder_kind", "device|offline"),
                ("holder_device_id", "uuid?"),
                ("holder_instance_id", "uuid"),
                ("share_commitment", "b32"),
            ],
            &[],
        )
        .map_err(|_| invalid())?;
        check(
            n(h, "share_index") == i as u64 + 1
                && (s(h, "holder_kind") == "offline") == h["holder_device_id"].is_null()
                && instances.insert(s(h, "holder_instance_id"))
                && shares.insert(s(h, "recovery_share_id")),
        )?;
        if !h["holder_device_id"].is_null() {
            check(devices.insert(s(h, "holder_device_id")))?;
        }
    }
    verify(v, admin, CTX_SET)
}
pub fn validate_status_chain(
    statuses: &[Value],
    manifest: &Value,
    known_hash: Option<&str>,
) -> Result {
    check(!statuses.is_empty())?;
    let mut previous: Option<&Value> = None;
    let mut found = known_hash.is_none();
    for status in statuses {
        shape(
            status,
            &[
                ("type", "PALLASYNC-RECOVERY-STATUS-v3"),
                ("version", "3.0"),
                ("recovery_set_id", "uuid"),
                ("set_manifest_hash", "b32"),
                ("status_seq", "int"),
                ("state", "prepared|active|retired|compromised"),
                ("previous_status_hash", "b32?"),
                ("replacement_set_id", "uuid?"),
                ("created_at_ms", "int"),
            ],
            true,
        )?;
        check(
            status["recovery_set_id"] == manifest["recovery_set_id"]
                && s(status, "set_manifest_hash") == object_hash(manifest),
        )?;
        check(
            status["replacement_set_id"].is_null()
                || matches!(s(status, "state"), "retired" | "compromised"),
        )?;
        if let Some(prev) = previous {
            check(
                n(status, "status_seq") == n(prev, "status_seq") + 1
                    && s(status, "previous_status_hash") == object_hash(prev),
            )?;
            check(matches!(
                (s(prev, "state"), s(status, "state")),
                ("prepared", "active" | "retired" | "compromised")
                    | ("active", "retired" | "compromised")
                    | ("retired", "compromised")
            ))?;
        } else {
            check(
                n(status, "status_seq") == 1
                    && status["previous_status_hash"].is_null()
                    && status["state"] == "prepared",
            )?;
        }
        verify(status, s(manifest, "admin_public_key"), CTX_STATUS)?;
        if known_hash.is_some_and(|h| h == object_hash(status)) {
            found = true;
        }
        previous = Some(status);
    }
    check(found)
}
pub fn validate_share(v: &Value, signed: bool) -> Result {
    shape(
        v,
        &[
            ("type", "PALLASYNC-RECOVERY-SHARE-v3"),
            ("version", "3.0"),
            ("recovery_share_id", "uuid"),
            ("recovery_set_id", "uuid"),
            ("chain_id", "b32"),
            ("generation", "u32"),
            ("threshold", "int"),
            ("total_shares", "int"),
            ("share_index", "int"),
            ("recovery_secret_fingerprint", "b32"),
            ("holder_kind", "device|offline"),
            ("holder_device_id", "uuid?"),
            ("holder_instance_id", "uuid"),
            ("share_wrap_key_id", "uuid"),
            (
                "share_wrap_key_protection",
                "os-keystore|hardware|webcrypto-nonextractable|passphrase-wrapped",
            ),
            ("share_wrap_nonce", "b24"),
            ("encrypted_share", "b48"),
            ("share_commitment", "b32"),
            ("recovery_blob_hash", "b32"),
            ("set_manifest_hash", "b32"),
            ("created_at_ms", "int"),
            ("admin_public_key", "b32"),
        ],
        signed,
    )?;
    profile(v)?;
    check(
        (1..=n(v, "total_shares")).contains(&n(v, "share_index"))
            && (s(v, "holder_kind") == "offline") == v["holder_device_id"].is_null(),
    )?;
    check(
        s(v, "holder_kind") != "offline"
            || s(v, "share_wrap_key_protection") == "passphrase-wrapped",
    )?;
    if signed {
        verify(v, s(v, "admin_public_key"), CTX_SHARE)?;
    }
    Ok(())
}
pub fn validate_offline(v: &Value, record: &Value) -> Result {
    fields(
        v,
        &[
            ("type", "PALLASYNC-OFFLINE-SHARE-KEY-v3"),
            ("version", "3.0"),
            ("recovery_set_id", "uuid"),
            ("recovery_share_id", "uuid"),
            ("holder_instance_id", "uuid"),
            ("share_wrap_key_id", "uuid"),
            ("kdf", "argon2id"),
            ("argon2_version", "int"),
            ("memory_kib", "int"),
            ("iterations", "int"),
            ("parallelism", "int"),
            ("salt", "b16"),
            ("key_nonce", "b24"),
            ("encrypted_key", "b48"),
        ],
        &[],
    )
    .map_err(|_| invalid())?;
    check(
        n(v, "argon2_version") == 19
            && n(v, "memory_kib") == 65536
            && n(v, "iterations") == 3
            && n(v, "parallelism") == 1,
    )?;
    for k in [
        "recovery_set_id",
        "recovery_share_id",
        "holder_instance_id",
        "share_wrap_key_id",
    ] {
        check(v[k] == record[k])?;
    }
    Ok(())
}
pub fn validate_package(v: &Value, admin: &str, known_status: Option<&str>) -> Result {
    check(canonical(v)?.len() <= MAX_PACKAGE)?;
    fields(
        v,
        &[
            ("type", "PALLASYNC-RECOVERY-PACKAGE-v3"),
            ("version", "3.0"),
            ("manifest", "object"),
            ("status_chain", "array"),
            ("record", "object"),
            ("blob", "object"),
            ("offline_key_envelope", "object?"),
        ],
        &[],
    )
    .map_err(|_| invalid())?;
    let m = &v["manifest"];
    let r = &v["record"];
    let b = &v["blob"];
    validate_manifest(m, admin)?;
    validate_blob(b)?;
    validate_share(r, true)?;
    validate_status_chain(a(v, "status_chain"), m, known_status)?;
    check(
        s(m, "recovery_blob_hash") == object_hash(b)
            && s(r, "recovery_blob_hash") == object_hash(b)
            && s(r, "set_manifest_hash") == object_hash(m),
    )?;
    for k in [
        "chain_id",
        "generation",
        "recovery_set_id",
        "threshold",
        "total_shares",
        "recovery_secret_fingerprint",
    ] {
        check(m[k] == r[k] && m[k] == b[k])?;
    }
    check(
        m["admin_public_key"] == r["admin_public_key"]
            && m["created_at_ms"] == b["created_at_ms"]
            && m["created_at_ms"] == r["created_at_ms"],
    )?;
    let holder = a(m, "holders")
        .get(n(r, "share_index") as usize - 1)
        .ok_or_else(invalid)?;
    for k in [
        "recovery_share_id",
        "share_index",
        "holder_kind",
        "holder_device_id",
        "holder_instance_id",
        "share_commitment",
    ] {
        check(holder[k] == r[k])?;
    }
    if s(r, "holder_kind") == "offline" {
        validate_offline(&v["offline_key_envelope"], r)?;
    } else {
        check(v["offline_key_envelope"].is_null())?;
    }
    Ok(())
}
pub fn commitment(set_id: &str, piece: &Piece) -> Result<String> {
    let id = uuid::Uuid::parse_str(set_id).map_err(|_| invalid())?;
    let mut hash = Sha256::new();
    hash.update(b"PALLASYNC-RECOVERY-SHARE-COMMIT-v3\0");
    hash.update(id.as_bytes());
    hash.update([piece.index]);
    hash.update(piece.bytes);
    Ok(URL_SAFE_NO_PAD.encode(hash.finalize()))
}
pub(crate) fn decode_piece(index: u8, encoded: &str) -> Result<Piece> {
    check(index != 0)?;
    let bytes = Zeroizing::new(decode(encoded, Some(32)).map_err(|_| invalid())?);
    Ok(Piece {
        index,
        bytes: bytes.as_slice().try_into().map_err(|_| invalid())?,
    })
}
fn aad(v: &Value, kind: &str, remove: &[&str]) -> Result<Vec<u8>> {
    let mut value = v.clone();
    let o = value.as_object_mut().ok_or_else(invalid)?;
    for key in remove {
        o.remove(*key);
    }
    value["type"] = json!(kind);
    canonical(&value)
}
pub fn unwrap_piece(record: &Value, rswk: &[u8; 32]) -> Result<Piece> {
    validate_share(record, true)?;
    let nonce = decode(s(record, "share_wrap_nonce"), Some(24)).map_err(|_| invalid())?;
    let ciphertext = decode(s(record, "encrypted_share"), Some(48)).map_err(|_| invalid())?;
    let aad = aad(
        record,
        "PALLASYNC-RECOVERY-SHARE-AAD-v3",
        &["signature", "share_wrap_nonce", "encrypted_share", "ext"],
    )?;
    let bytes = Zeroizing::new(
        XChaCha20Poly1305::new(rswk.into())
            .decrypt(
                XNonce::from_slice(&nonce),
                Payload {
                    msg: &ciphertext,
                    aad: &aad,
                },
            )
            .map_err(|_| invalid())?,
    );
    let piece = Piece {
        index: n(record, "share_index") as u8,
        bytes: bytes.as_slice().try_into().map_err(|_| invalid())?,
    };
    check(s(record, "share_commitment") == commitment(s(record, "recovery_set_id"), &piece)?)?;
    Ok(piece)
}

#[allow(clippy::too_many_arguments)]
pub fn wrap_proposal(
    manifest: &Value,
    blob: &Value,
    piece: &Piece,
    rswk: &[u8; 32],
    wrap_key_id: &str,
    protection: &str,
) -> Result<Value> {
    validate_manifest(manifest, s(manifest, "admin_public_key"))?;
    validate_blob(blob)?;
    let holder = a(manifest, "holders")
        .get(
            usize::from(piece.index)
                .checked_sub(1)
                .ok_or_else(invalid)?,
        )
        .ok_or_else(invalid)?;
    check(
        commitment(s(manifest, "recovery_set_id"), piece)? == s(holder, "share_commitment")
            && s(manifest, "recovery_blob_hash") == object_hash(blob),
    )?;
    let mut record = holder.clone();
    for k in [
        "chain_id",
        "generation",
        "recovery_set_id",
        "threshold",
        "total_shares",
        "recovery_secret_fingerprint",
        "admin_public_key",
    ] {
        record[k] = manifest[k].clone();
    }
    record["type"] = json!("PALLASYNC-RECOVERY-SHARE-v3");
    record["version"] = json!("3.0");
    record["share_wrap_key_id"] = json!(wrap_key_id);
    record["share_wrap_key_protection"] = json!(protection);
    record["recovery_blob_hash"] = json!(object_hash(blob));
    record["set_manifest_hash"] = json!(object_hash(manifest));
    record["created_at_ms"] = manifest["created_at_ms"].clone();
    let associated = aad(&record, "PALLASYNC-RECOVERY-SHARE-AAD-v3", &[])?;
    let nonce = crypto::generate_nonce_24();
    let ciphertext = XChaCha20Poly1305::new(rswk.into())
        .encrypt(
            XNonce::from_slice(&nonce),
            Payload {
                msg: &piece.bytes,
                aad: &associated,
            },
        )
        .map_err(|_| invalid())?;
    record["share_wrap_nonce"] = json!(URL_SAFE_NO_PAD.encode(nonce));
    record["encrypted_share"] = json!(URL_SAFE_NO_PAD.encode(ciphertext));
    validate_share(&record, false)?;
    Ok(record)
}

fn credential_key(credential: &[u8], salt: &[u8]) -> Result<Zeroizing<[u8; 32]>> {
    let normalized = Zeroizing::new(
        std::str::from_utf8(credential)
            .map_err(|_| ClientError("C_RECOVERY_WRAP_KEY_UNAVAILABLE"))?
            .nfkd()
            .collect::<String>(),
    );
    if normalized.is_empty() {
        return Err(ClientError("C_RECOVERY_WRAP_KEY_UNAVAILABLE"));
    }
    let params = argon2::Params::new(65536, 3, 1, Some(32)).map_err(|_| invalid())?;
    let mut key = Zeroizing::new([0; 32]);
    argon2::Argon2::new(argon2::Algorithm::Argon2id, argon2::Version::V0x13, params)
        .hash_password_into(normalized.as_bytes(), salt, &mut *key)
        .map_err(|_| ClientError("C_RECOVERY_WRAP_KEY_UNAVAILABLE"))?;
    Ok(key)
}
pub fn wrap_offline_key(record: &Value, rswk: &[u8; 32], credential: &[u8]) -> Result<Value> {
    check(record["holder_kind"] == "offline")?;
    let salt = crypto::generate_nonce_16();
    let nonce = crypto::generate_nonce_24();
    let key = credential_key(credential, &salt)?;
    let mut envelope = json!({"type":"PALLASYNC-OFFLINE-SHARE-KEY-v3","version":"3.0","recovery_set_id":record["recovery_set_id"],"recovery_share_id":record["recovery_share_id"],"holder_instance_id":record["holder_instance_id"],"share_wrap_key_id":record["share_wrap_key_id"],"kdf":"argon2id","argon2_version":19,"memory_kib":65536,"iterations":3,"parallelism":1,"salt":URL_SAFE_NO_PAD.encode(salt)});
    let associated = canonical(&envelope)?;
    let encrypted = XChaCha20Poly1305::new((&*key).into())
        .encrypt(
            XNonce::from_slice(&nonce),
            Payload {
                msg: rswk,
                aad: &associated,
            },
        )
        .map_err(|_| invalid())?;
    envelope["key_nonce"] = json!(URL_SAFE_NO_PAD.encode(nonce));
    envelope["encrypted_key"] = json!(URL_SAFE_NO_PAD.encode(encrypted));
    validate_offline(&envelope, record)?;
    Ok(envelope)
}
pub fn unwrap_offline_key(package: &Value, credential: &[u8]) -> Result<Zeroizing<[u8; 32]>> {
    let record = &package["record"];
    let envelope = &package["offline_key_envelope"];
    validate_offline(envelope, record)?;
    let key = credential_key(
        credential,
        &decode(s(envelope, "salt"), Some(16)).map_err(|_| invalid())?,
    )?;
    let mut associated = envelope.clone();
    let o = associated.as_object_mut().ok_or_else(invalid)?;
    o.remove("key_nonce");
    o.remove("encrypted_key");
    let bytes = Zeroizing::new(
        XChaCha20Poly1305::new((&*key).into())
            .decrypt(
                XNonce::from_slice(
                    &decode(s(envelope, "key_nonce"), Some(24)).map_err(|_| invalid())?,
                ),
                Payload {
                    msg: &decode(s(envelope, "encrypted_key"), Some(48)).map_err(|_| invalid())?,
                    aad: &canonical(&associated)?,
                },
            )
            .map_err(|_| ClientError("C_RECOVERY_WRAP_KEY_UNAVAILABLE"))?,
    );
    let mut rswk = Zeroizing::new([0; 32]);
    rswk.copy_from_slice(&bytes);
    Ok(rswk)
}

#[derive(Zeroize, ZeroizeOnDrop)]
pub struct RecoveryInput {
    pub mnemonic: String,
    pub passphrase: String,
}
impl RecoveryInput {
    pub fn from_user(mnemonic: &str, passphrase: &str) -> Result<Self> {
        let phrase = bip39::Mnemonic::parse_in(bip39::Language::English, mnemonic)
            .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        check(phrase.word_count() == 24)?;
        Ok(Self {
            mnemonic: phrase.to_string(),
            passphrase: passphrase.nfkd().collect(),
        })
    }
    pub fn fingerprint(&self) -> Result<String> {
        let value = SecretJson(json!({"mnemonic":self.mnemonic,"passphrase":self.passphrase}));
        let mut bytes = Zeroizing::new(canonical(&value.0)?);
        let mut hash = Sha256::new();
        hash.update(b"PALLASYNC-RECOVERY-SECRET-FP-v3\0");
        hash.update(&*bytes);
        bytes.zeroize();
        Ok(URL_SAFE_NO_PAD.encode(hash.finalize()))
    }
    pub fn roots(&self) -> Result<RootKeys> {
        crypto::derive_root_keys(&self.mnemonic, &self.passphrase)
            .map_err(|_| ClientError("C_RECOVERY_ROOT_MISMATCH"))
    }
}

pub fn open_blob(blob: &Value, rms: &[u8; 32], expected_admin: &str) -> Result<RecoveryInput> {
    validate_blob(blob)?;
    let nonce = decode(s(blob, "blob_nonce"), Some(24)).map_err(|_| invalid())?;
    let ciphertext = decode(s(blob, "encrypted_blob"), None).map_err(|_| invalid())?;
    let associated = aad(
        blob,
        "PALLASYNC-RECOVERY-BLOB-AAD-v3",
        &["blob_nonce", "encrypted_blob"],
    )?;
    let frame = Zeroizing::new(
        XChaCha20Poly1305::new(rms.into())
            .decrypt(
                XNonce::from_slice(&nonce),
                Payload {
                    msg: &ciphertext,
                    aad: &associated,
                },
            )
            .map_err(|_| ClientError("C_RECOVERY_BLOB_INVALID"))?,
    );
    let bytes = Zeroizing::new(
        crypto::unpad_and_unframe(&frame, crypto::SIZE_BUCKETS)
            .map_err(|_| ClientError("C_RECOVERY_BLOB_INVALID"))?,
    );
    let plaintext =
        SecretJson(jcs::parse(&bytes).map_err(|_| ClientError("C_RECOVERY_BLOB_INVALID"))?);
    let plaintext = &plaintext.0;

    (|| {
        fields(
            plaintext,
            &[
                ("mnemonic", "text"),
                ("passphrase", "text"),
                ("created_at_ms", "int"),
                ("recovery_set_id", "uuid"),
                ("recovery_secret_fingerprint", "b32"),
                ("chain_id", "b32"),
                ("generation", "u32"),
            ],
            &[],
        )
        .map_err(|_| ClientError("C_RECOVERY_BLOB_INVALID"))?;
        let input = RecoveryInput::from_user(s(plaintext, "mnemonic"), s(plaintext, "passphrase"))?;
        check(
            input.mnemonic == s(plaintext, "mnemonic")
                && input.passphrase == s(plaintext, "passphrase"),
        )?;
        for k in [
            "chain_id",
            "generation",
            "recovery_set_id",
            "recovery_secret_fingerprint",
            "created_at_ms",
        ] {
            check(plaintext[k] == blob[k])?;
        }
        let roots = input.roots()?;
        if !bool::from(
            input
                .fingerprint()?
                .as_bytes()
                .ct_eq(s(blob, "recovery_secret_fingerprint").as_bytes()),
        ) || roots.chain_id != s(blob, "chain_id")
            || roots.admin_public_key != expected_admin
        {
            return Err(ClientError("C_RECOVERY_ROOT_MISMATCH"));
        }
        Ok(input)
    })()
}
pub struct SecretJson(pub Value);
impl Drop for SecretJson {
    fn drop(&mut self) {
        zeroize_json(&mut self.0);
    }
}
pub fn zeroize_json(v: &mut Value) {
    match v {
        Value::String(s) => s.zeroize(),
        Value::Array(a) => a.iter_mut().for_each(zeroize_json),
        Value::Object(o) => o.values_mut().for_each(zeroize_json),
        _ => {}
    }
}

pub fn portable(package: &Value) -> Result<String> {
    check(canonical(package)?.len() <= MAX_PACKAGE)?;
    Ok(format!(
        "PSR3:{}",
        URL_SAFE_NO_PAD.encode(canonical(package)?)
    ))
}
pub fn parse_portable(encoded: &str) -> Result<Value> {
    check(encoded.len() <= 5 + MAX_PACKAGE.div_ceil(3) * 4)?;
    let data =
        decode(encoded.strip_prefix("PSR3:").ok_or_else(invalid)?, None).map_err(|_| invalid())?;
    let v = parse(&data)?;
    check(canonical(&v)? == data)?;
    Ok(v)
}

/// §7.11.4 numbered physical fragments carry transport integrity, never authority.
pub fn fragments(package: &Value, part_bytes: usize) -> Result<Vec<Value>> {
    check(part_bytes > 0)?;
    let encoded = portable(package)?;
    let count = encoded.len().div_ceil(part_bytes);
    check(count <= 4096)?;
    let hash = crate::strict::verify::hash_bytes(encoded.as_bytes());
    Ok(encoded.as_bytes().chunks(part_bytes).enumerate().map(|(i,data)|json!({"package_hash":hash,"part_index":i+1,"part_count":count,"data":std::str::from_utf8(data).unwrap_or("")})).collect())
}
#[derive(Default)]
pub struct FragmentAssembly {
    hash: Option<String>,
    count: usize,
    parts: std::collections::BTreeMap<usize, String>,
    size: usize,
}
impl FragmentAssembly {
    pub fn accept(&mut self, fragment: &Value) -> Result<Option<Value>> {
        fields(
            fragment,
            &[
                ("package_hash", "b32"),
                ("part_index", "int"),
                ("part_count", "int"),
                ("data", "text"),
            ],
            &[],
        )
        .map_err(|_| invalid())?;
        let count = n(fragment, "part_count") as usize;
        let index = n(fragment, "part_index") as usize;
        let hash = s(fragment, "package_hash");
        let data = s(fragment, "data");
        check((1..=4096).contains(&count) && (1..=count).contains(&index) && data.is_ascii())?;
        if let Some(known) = &self.hash {
            check(known == hash && self.count == count)?;
        }
        if let Some(previous) = self.parts.get(&index) {
            check(previous == data)?;
        } else {
            let size = self.size.checked_add(data.len()).ok_or_else(invalid)?;
            check(size <= 5 + MAX_PACKAGE.div_ceil(3) * 4)?;
            self.size = size;
            self.parts.insert(index, data.to_owned());
        }
        self.hash = Some(hash.to_owned());
        self.count = count;
        if self.parts.len() != count {
            return Ok(None);
        }
        let encoded =
            self.parts
                .values()
                .fold(String::with_capacity(self.size), |mut out, part| {
                    out.push_str(part);
                    out
                });
        check(crate::strict::verify::hash_bytes(encoded.as_bytes()) == hash)?;
        let package = parse_portable(&encoded)?;
        Ok(Some(package))
    }
}
