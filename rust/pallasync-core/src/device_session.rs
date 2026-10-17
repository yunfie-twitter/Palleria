//! Scoped device authoring sessions. Private keys cross JNI as mutable bytes, never JSON (§5.4/§11.1).
use crate::{
    client_error::{ClientError, Result},
    crypto,
    locked_memory::Locked,
    models::{InnerRecord, SyncRecord},
};
use ed25519_dalek::SigningKey;
use serde::Deserialize;
use std::{
    collections::HashMap,
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicU64, Ordering},
    },
    time::{Duration, Instant},
};
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

#[derive(Zeroize, ZeroizeOnDrop)]
struct Keys {
    signing: [u8; 32],
    record: Option<[u8; 32]>,
}
struct Session {
    keys: Locked<Keys>,
    touched: Instant,
}
static NEXT: AtomicU64 = AtomicU64::new(1);
static SESSIONS: OnceLock<Mutex<HashMap<u64, Session>>> = OnceLock::new();
const IDLE: Duration = Duration::from_secs(600);
const MAX_SESSIONS: usize = 16;
fn sessions() -> &'static Mutex<HashMap<u64, Session>> {
    SESSIONS.get_or_init(|| Mutex::new(HashMap::new()))
}

pub fn begin(signing: &[u8], record: &[u8]) -> Result<u64> {
    crate::secret_lifetime::start();
    if signing.len() != 32 || (!record.is_empty() && record.len() != 32) {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    }
    let keys = Keys {
        signing: signing
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        record: if record.is_empty() {
            None
        } else {
            Some(
                record
                    .try_into()
                    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            )
        },
    };
    let keys = Locked::new(keys)?;
    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, s| s.touched.elapsed() < IDLE);
    if all.len() >= MAX_SESSIONS {
        return Err(ClientError("C_LOCAL_LIMIT"));
    }
    let handle = NEXT
        .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |n| {
            n.checked_add(1).filter(|n| *n <= i64::MAX as u64)
        })
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.insert(
        handle,
        Session {
            keys,
            touched: Instant::now(),
        },
    );
    Ok(handle)
}
pub fn end(handle: u64) {
    if let Ok(mut all) = sessions().lock() {
        all.remove(&handle);
    }
}
pub(crate) fn expire() {
    if let Some(all) = SESSIONS.get()
        && let Ok(mut all) = all.lock()
    {
        all.retain(|_, s| s.touched.elapsed() < IDLE);
    }
}

/// This is an internal operation input, not a new REST or signed object.
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct RecordContext {
    pub chain_id: String,
    pub generation: u32,
    pub record_id: String,
    pub device_id: String,
    pub epoch: u32,
    #[serde(deserialize_with = "crate::models::required_nullable")]
    pub collection_tag: Option<String>,
}
pub fn prepare_frame(inner_json: &[u8]) -> Result<Vec<u8>> {
    let value = crate::recovery::SecretJson(
        crate::strict::jcs::parse(inner_json).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    );
    crate::schema::validate_inner(&value.0)?;
    let canonical = Zeroizing::new(
        crate::strict::jcs::canonicalize_json(&value.0)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    );
    crypto::pad_and_frame(&canonical, crypto::SIZE_BUCKETS, false)
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))
}
pub fn create(
    handle: u64,
    context: RecordContext,
    frame: &[u8],
    nonce: &[u8],
) -> Result<SyncRecord> {
    let nonce: &[u8; 24] = nonce
        .try_into()
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, s| s.touched.elapsed() < IDLE);
    let session = all
        .get_mut(&handle)
        .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
    session.touched = Instant::now();
    crypto::create_sync_record_from_frame(
        &context.chain_id,
        context.generation,
        &context.record_id,
        &context.device_id,
        context.epoch,
        context.collection_tag.as_deref(),
        frame,
        nonce,
        session
            .keys
            .record
            .as_ref()
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))?,
        &SigningKey::from_bytes(&session.keys.signing),
    )
    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
}

/// Read back an owned immutable outbox object before a permitted rebuild (§11.1).
/// This does not confer registry/log acceptance on the object.
pub fn decrypt_own(handle: u64, value: &serde_json::Value) -> Result<InnerRecord> {
    crate::public_wire::validate(value, "record")?;
    let record: SyncRecord =
        serde_json::from_value(value.clone()).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, s| s.touched.elapsed() < IDLE);
    let session = all
        .get_mut(&handle)
        .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
    session.touched = Instant::now();
    let public = SigningKey::from_bytes(&session.keys.signing).verifying_key();
    use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
    if !crate::strict::verify::verify_signed_json(
        &URL_SAFE_NO_PAD.encode(public.as_bytes()),
        &record.signature,
        value,
        crate::strict::verify::CTX_SYNC_RECORD,
    )
    .unwrap_or(false)
    {
        return Err(ClientError("C_SIGNATURE_INVALID"));
    }
    crypto::decrypt_sync_record(
        &record,
        session
            .keys
            .record
            .as_ref()
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))?,
    )
    .map_err(|e| {
        if e == "C_AEAD_FAILURE" {
            ClientError("C_AEAD_FAILURE")
        } else {
            ClientError("C_SCHEMA_VIOLATION")
        }
    })
}

/// Sign the exact §7.8 CompactionRecord under the current device key. This does not authorize
/// it against active membership; the coordinator must establish that from verified authority.
pub fn sign_compaction(
    handle: u64,
    mut value: serde_json::Value,
    expected_device_id: &str,
    expected_public_key: &str,
) -> Result<serde_json::Value> {
    use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};

    let object = value
        .as_object_mut()
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
    object.insert(
        "signature".into(),
        serde_json::Value::String(URL_SAFE_NO_PAD.encode([0; 64])),
    );
    crate::public_wire::validate(&value, "compaction")?;
    if crate::public_wire::s(&value, "device_id") != expected_device_id {
        return Err(ClientError("C_DEVICE_KEY_MISMATCH"));
    }

    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, s| s.touched.elapsed() < IDLE);
    let session = all
        .get_mut(&handle)
        .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
    session.touched = Instant::now();
    let signing = SigningKey::from_bytes(&session.keys.signing);
    if URL_SAFE_NO_PAD.encode(signing.verifying_key().as_bytes()) != expected_public_key {
        return Err(ClientError("C_DEVICE_KEY_MISMATCH"));
    }
    let canonical = crate::models::unsigned_record_jcs(&value)
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    value["signature"] = serde_json::Value::String(crate::crypto::sign_with_context(
        &signing,
        crate::crypto::CTX_COMPACTION,
        &canonical,
    ));
    crate::public_wire::validate(&value, "compaction")?;
    crate::public_wire::verify(&value, expected_public_key, crate::crypto::CTX_COMPACTION)?;
    Ok(value)
}

pub fn sign_checkpoint(
    handle: u64,
    value: &mut serde_json::Value,
    expected_device_id: &str,
    certified_public_key: &str,
) -> Result<serde_json::Value> {
    if crate::public_wire::s(value, "device_id") != expected_device_id {
        return Err(ClientError("C_DEVICE_KEY_MISMATCH"));
    }
    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, session| session.touched.elapsed() < IDLE);
    let session = all
        .get_mut(&handle)
        .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
    session.touched = Instant::now();
    crate::checkpoint::sign(value, &session.keys.signing, certified_public_key)?;
    Ok(value.clone())
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct TokenBinding {
    pub aud: String,
    pub chain_id: String,
    pub device_id: String,
    pub method: String,
    pub path: String,
    pub query: String,
}
/// Device capabilities never turn a raw signing seed into administrative authority (§6.4).
pub fn token(handle: u64, binding: TokenBinding, body: &[u8]) -> Result<String> {
    token_at(
        handle,
        binding,
        body,
        crypto::token_now_ms().map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    )
}
pub fn token_at(handle: u64, binding: TokenBinding, body: &[u8], now: i64) -> Result<String> {
    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, s| s.touched.elapsed() < IDLE);
    let session = all
        .get_mut(&handle)
        .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
    session.touched = Instant::now();
    crypto::create_capability_token_at(
        &binding.aud,
        &binding.chain_id,
        "device",
        Some(&binding.device_id),
        &binding.method,
        &binding.path,
        &binding.query,
        body,
        &SigningKey::from_bytes(&session.keys.signing),
        120_000,
        now,
    )
    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
}
