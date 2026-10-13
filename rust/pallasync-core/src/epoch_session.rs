//! Transient epoch cryptography; the JVM receives no derived secret JSON (§5.3/§11.2).
use crate::{
    client_error::{ClientError, Result},
    crypto,
    locked_memory::Locked,
    models::{DeviceRecord, InnerRecord, SyncRecord},
    public_wire::{self as wire, fields},
    strict::verify::decode,
};
use serde::Deserialize;
use serde_json::Value;
use std::{
    collections::HashMap,
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicU64, Ordering},
    },
    time::{Duration, Instant},
};
use subtle::ConstantTimeEq;
use zeroize::Zeroizing;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Context {
    pub chain_id: String,
    pub generation: u32,
    pub epoch: u32,
    pub epoch_commitment: String,
    pub metadata_profile: String,
}
struct Session {
    context: Context,
    keys: Locked<crypto::EpochKeys>,
    touched: Instant,
}
static ALL: OnceLock<Mutex<HashMap<u64, Session>>> = OnceLock::new();
static NEXT: AtomicU64 = AtomicU64::new(1);
const IDLE: Duration = Duration::from_secs(600);
fn sessions() -> &'static Mutex<HashMap<u64, Session>> {
    ALL.get_or_init(|| Mutex::new(HashMap::new()))
}

pub fn begin(context: Context, secret: &[u8]) -> Result<u64> {
    fields(
        &serde_json::json!({"chain_id":context.chain_id,"generation":context.generation,"epoch":context.epoch,
        "epoch_commitment":context.epoch_commitment,"metadata_profile":context.metadata_profile}),
        &[
            ("chain_id", "b32"),
            ("generation", "u32"),
            ("epoch", "u32"),
            ("epoch_commitment", "b32"),
            ("metadata_profile", "private|queryable"),
        ],
        &[],
    )?;
    let chain: [u8; 32] = decode(&context.chain_id, Some(32))
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?
        .try_into()
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let secret = Zeroizing::new(
        <[u8; 32]>::try_from(secret).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    );
    let keys = crypto::derive_epoch_keys(&chain, context.epoch, &secret)
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let commitment = decode(&context.epoch_commitment, Some(32))
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    if !bool::from(keys.epoch_commitment.as_slice().ct_eq(&commitment)) {
        return Err(ClientError("C_COMMITMENT_MISMATCH"));
    }
    let keys = Locked::new(keys)?;
    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, s| s.touched.elapsed() < IDLE);
    if all.len() >= 16 {
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
            context,
            keys,
            touched: Instant::now(),
        },
    );
    crate::secret_lifetime::start();
    Ok(handle)
}
pub fn end(handle: u64) {
    if let Ok(mut all) = sessions().lock() {
        all.remove(&handle);
    }
}
pub(crate) fn expire() {
    if let Some(all) = ALL.get()
        && let Ok(mut all) = all.lock()
    {
        all.retain(|_, s| s.touched.elapsed() < IDLE);
    }
}
fn with<T>(handle: u64, f: impl FnOnce(&Session) -> Result<T>) -> Result<T> {
    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, s| s.touched.elapsed() < IDLE);
    let s = all
        .get_mut(&handle)
        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
    s.touched = Instant::now();
    f(s)
}
pub fn tag(handle: u64, collection: &str) -> Result<Option<String>> {
    with(handle, |s| {
        Ok(if s.context.metadata_profile == "private" {
            None
        } else {
            Some(crypto::compute_collection_tag(
                &s.keys.collection_tag_key,
                collection,
            ))
        })
    })
}
pub fn decrypt_record(handle: u64, value: &Value) -> Result<InnerRecord> {
    wire::validate(value, "record")?;
    let record: SyncRecord =
        serde_json::from_value(value.clone()).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    with(handle, |s| {
        if record.chain_id != s.context.chain_id
            || record.generation != s.context.generation
            || record.epoch != s.context.epoch
        {
            return Err(ClientError("C_MISSING_DEPENDENCY"));
        }
        let inner = crypto::decrypt_sync_record(&record, &s.keys.record_key).map_err(classify)?;
        let expected = if s.context.metadata_profile == "private" {
            None
        } else {
            Some(crypto::compute_collection_tag(
                &s.keys.collection_tag_key,
                &inner.collection,
            ))
        };
        if expected.is_none() != record.collection_tag.is_none() {
            return Err(ClientError("C_SCHEMA_VIOLATION"));
        }
        if let (Some(expected), Some(actual)) = (expected, &record.collection_tag)
            && !bool::from(expected.as_bytes().ct_eq(actual.as_bytes()))
        {
            return Err(ClientError("C_COLLECTION_TAG_MISMATCH"));
        }
        Ok(inner)
    })
}
pub fn decrypt_device(handle: u64, value: &Value) -> Result<Value> {
    wire::validate(value, "device")?;
    let record: DeviceRecord =
        serde_json::from_value(value.clone()).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    with(handle, |s| {
        if record.chain_id != s.context.chain_id
            || record.generation != s.context.generation
            || record.meta_epoch != s.context.epoch
        {
            return Err(ClientError("C_MISSING_DEPENDENCY"));
        }
        let meta =
            crypto::decrypt_device_meta(&record, &s.keys.device_meta_key).map_err(classify)?;
        serde_json::to_value(meta).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    })
}

/// The caller supplies the exact envelope whose original receipt and history were verified.
/// Check actual possession of the recipient's DH key before acknowledging delivery (§10.8).
pub fn verify_device_envelope(handle: u64, value: &Value, private: &[u8]) -> Result {
    wire::validate(value, "envelope")?;
    let envelope: crate::models::EpochKeyEnvelope =
        serde_json::from_value(value.clone()).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let private = Zeroizing::new(
        <[u8; 32]>::try_from(private).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    );
    let secret = x25519_dalek::StaticSecret::from(*private);
    let public = x25519_dalek::PublicKey::from(&secret);
    if envelope.recipient_key_hash.as_deref()
        != Some(&crate::strict::verify::hash_bytes(public.as_bytes()))
    {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    }
    with(handle, |s| {
        if envelope.chain_id != s.context.chain_id
            || envelope.generation != s.context.generation
            || envelope.epoch != s.context.epoch
            || envelope.epoch_commitment != s.context.epoch_commitment
        {
            return Err(ClientError("C_MISSING_DEPENDENCY"));
        }
        let chain = decode(&s.context.chain_id, Some(32))
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let unwrapped = Zeroizing::new(
            crypto::unwrap_device_epoch_envelope(&envelope, &private, &chain).map_err(classify)?,
        );
        let derived = crypto::derive_epoch_keys(&chain, s.context.epoch, &unwrapped)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        if !bool::from(derived.record_key.ct_eq(&s.keys.record_key)) {
            return Err(ClientError("C_COMMITMENT_MISMATCH"));
        }
        Ok(())
    })
}
/// Normal keys only; callers transfer mutable ownership to OS protected storage.
pub(crate) fn normal_material(
    context: Context,
    secret: &[u8],
) -> Result<Locked<crate::rotation::Material>> {
    let owned = Zeroizing::new(
        <[u8; 32]>::try_from(secret).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    );
    let handle = begin(context, &*owned)?;
    let result = with(handle, |s| {
        Locked::new(crate::rotation::Material {
            record: s.keys.record_key,
            metadata: s.keys.device_meta_key,
            tag: s.keys.collection_tag_key,
            secret: *owned,
        })
    });
    end(handle);
    result
}

pub fn open_own_envelope(
    authority: &crate::authority::Authority,
    entry: &Value,
    receipt: &Value,
    device: &str,
    private: &[u8],
) -> Result<Zeroizing<[u8; 32]>> {
    crate::attestations::validate_log_entry(entry)?;
    if entry["kind"] != "envelope" || entry["object"]["recipient_device_id"] != device {
        return Err(ClientError("C_ACCEPTANCE_UNPROVEN"));
    }
    let envelope = &entry["object"];
    authority.device_envelope_eligible(envelope, crate::public_wire::n(entry, "relay_seq"))?;
    crate::attestations::verify_receipt(receipt, envelope, Some(entry), &authority.pin)?;
    let private = Zeroizing::new(
        <[u8; 32]>::try_from(private).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    );
    let secret = x25519_dalek::StaticSecret::from(*private);
    let public = x25519_dalek::PublicKey::from(&secret);
    if envelope["recipient_key_hash"] != crate::strict::verify::hash_bytes(public.as_bytes()) {
        return Err(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"));
    }
    let envelope =
        serde_json::from_value(envelope.clone()).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let chain = decode(&authority.pin.chain_id, Some(32))
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?
        .try_into()
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    crypto::unwrap_device_epoch_envelope(&envelope, &private, &chain)
        .map(Zeroizing::new)
        .map_err(classify)
}

fn classify(error: String) -> ClientError {
    match error.as_str() {
        "C_AEAD_FAILURE" => ClientError("C_AEAD_FAILURE"),
        "C_COMMITMENT_MISMATCH" => ClientError("C_COMMITMENT_MISMATCH"),
        "C_UNSUPPORTED_SCHEMA" => ClientError("C_UNSUPPORTED_SCHEMA"),
        _ => ClientError("C_SCHEMA_VIOLATION"),
    }
}
