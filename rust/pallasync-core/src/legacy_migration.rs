//! Strict plaintext conversion primitives for the explicit v2.1 -> v3 migration (§9).
//! This module does not resolve a legacy chain's frozen winner state or assign v3 stamps.
//! Those require the complete verified source export and are deliberately left to the
//! migration coordinator, which journals its decisions before authoring destination records.

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value, json};
use std::collections::BTreeSet;
use std::{
    collections::HashMap,
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicU64, Ordering},
    },
    time::{Duration, Instant},
};
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

use crate::{
    crypto::{LegacyV20Keys, LegacyV21Keys},
    locked_memory::Locked,
};
use unicode_normalization::UnicodeNormalization;

const MAX_SAFE_INTEGER: u64 = 9_007_199_254_740_991;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct DataPayloadV21 {
    schema: String,
    entity_id: String,
    operation: String,
    context: Value,
    lamport: u64,
    created_at_ms: i64,
    body: Value,
}

/// A verified legacy entity candidate ready for frozen-state conflict resolution.
/// It intentionally has no v3 Lamport, timestamp or favorite order key yet.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
pub struct LegacyEntityCandidate {
    pub collection: String,
    pub legacy_entity_id: String,
    pub entity_id: String,
    pub operation: String,
    pub body: Value,
    pub legacy_lamport: u64,
    pub legacy_created_at_ms: i64,
    pub normalization_changed: bool,
    pub normalization_collision: bool,
}

#[derive(Zeroize, ZeroizeOnDrop)]
enum SessionKeys {
    V20(LegacyV20Keys),
    V21(LegacyV21Keys),
}

struct Session {
    keys: Locked<SessionKeys>,
    lease: crate::secret_lifetime::Lease,
    touched: Instant,
}

static NEXT: AtomicU64 = AtomicU64::new(1);
static SESSIONS: OnceLock<Mutex<HashMap<u64, Session>>> = OnceLock::new();
const IDLE: Duration = Duration::from_secs(600);
const MAX_SESSIONS: usize = 8;

fn sessions() -> &'static Mutex<HashMap<u64, Session>> {
    SESSIONS.get_or_init(|| Mutex::new(HashMap::new()))
}

/// Begin a bounded, memory-locked native legacy conversion session after matching the
/// explicit old-chain public identity (§9.1). The source export and trusted registry keys
/// still must be obtained and checked by the migration coordinator.
pub fn begin(
    seed_phrase: &str,
    passphrase: &str,
    chain_salt: &[u8; 32],
    chain_id: &str,
    admin_public_key: &str,
) -> crate::client_error::Result<u64> {
    crate::secret_lifetime::start();
    let keys = crate::crypto::derive_legacy_v21_keys(seed_phrase, passphrase, chain_salt)
        .map_err(|_| crate::client_error::ClientError("C_SCHEMA_VIOLATION"))?;
    let salt = base64::Engine::encode(
        &base64::engine::general_purpose::URL_SAFE_NO_PAD,
        chain_salt,
    );
    if !crate::crypto::legacy_v21_identity_matches(&keys, chain_id, &salt, admin_public_key)
        .unwrap_or(false)
    {
        return Err(crate::client_error::ClientError(
            "C_BOOTSTRAP_TRUST_REQUIRED",
        ));
    }
    let lease = crate::secret_lifetime::lease()?;
    let keys = Locked::new(SessionKeys::V21(keys))?;
    let mut all = sessions()
        .lock()
        .map_err(|_| crate::client_error::ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, session| session.touched.elapsed() < IDLE && session.lease.live());
    if all.len() >= MAX_SESSIONS {
        return Err(crate::client_error::ClientError("C_LOCAL_LIMIT"));
    }
    let handle = NEXT
        .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |next| {
            next.checked_add(1).filter(|next| *next <= i64::MAX as u64)
        })
        .map_err(|_| crate::client_error::ClientError("C_LOCAL_LIMIT"))?;
    all.insert(
        handle,
        Session {
            keys,
            lease,
            touched: Instant::now(),
        },
    );
    Ok(handle)
}

/// Begin a v2.0 migration session. Its chain ID is deterministic from the mnemonic, unlike
/// v2.1's random salt; no guessed v2.1 salt or unrecognized signature context is accepted.
pub fn begin_v20(
    seed_phrase: &str,
    passphrase: &str,
    chain_id: &str,
) -> crate::client_error::Result<u64> {
    crate::secret_lifetime::start();
    let keys = crate::crypto::derive_legacy_v20_keys(seed_phrase, passphrase)
        .map_err(|_| crate::client_error::ClientError("C_SCHEMA_VIOLATION"))?;
    if keys.chain_id != chain_id {
        return Err(crate::client_error::ClientError(
            "C_BOOTSTRAP_TRUST_REQUIRED",
        ));
    }
    let lease = crate::secret_lifetime::lease()?;
    let keys = Locked::new(SessionKeys::V20(keys))?;
    let mut all = sessions()
        .lock()
        .map_err(|_| crate::client_error::ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, session| session.touched.elapsed() < IDLE && session.lease.live());
    if all.len() >= MAX_SESSIONS {
        return Err(crate::client_error::ClientError("C_LOCAL_LIMIT"));
    }
    let handle = NEXT
        .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |next| {
            next.checked_add(1).filter(|next| *next <= i64::MAX as u64)
        })
        .map_err(|_| crate::client_error::ClientError("C_LOCAL_LIMIT"))?;
    all.insert(
        handle,
        Session {
            keys,
            lease,
            touched: Instant::now(),
        },
    );
    Ok(handle)
}

/// Authenticate, decrypt and convert exactly one old record. The supplied key must already
/// have been matched to the selected source registry entry by the caller. Output is a
/// zeroizing UTF-8 byte buffer, never an immutable JNI String or a persisted secret.
pub fn convert_record(
    handle: u64,
    record_json: &str,
    expected_device_id: &str,
    device_public_key: &str,
) -> crate::client_error::Result<Zeroizing<Vec<u8>>> {
    let mut all = sessions()
        .lock()
        .map_err(|_| crate::client_error::ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, session| session.touched.elapsed() < IDLE && session.lease.live());
    let session = all
        .get_mut(&handle)
        .ok_or(crate::client_error::ClientError(
            "C_BOOTSTRAP_TRUST_REQUIRED",
        ))?;
    session
        .lease
        .check()
        .map_err(|_| crate::client_error::ClientError("C_RECOVERY_SESSION_EXPIRED"))?;
    session.touched = Instant::now();
    let record_value = crate::strict::jcs::parse(record_json.as_bytes())
        .map_err(|_| crate::client_error::ClientError("C_SCHEMA_VIOLATION"))?;
    let (source_chain, source_device, collection, action, binding, plaintext) = match &*session.keys
    {
        SessionKeys::V20(keys) => {
            let record: crate::models::legacy::SyncRecordV20 =
                serde_json::from_value(record_value.clone())
                    .map_err(|_| crate::client_error::ClientError("C_SCHEMA_VIOLATION"))?;
            let plaintext = crate::crypto::decrypt_sync_record_v20_json(record_json, keys)
                .map_err(|code| {
                    crate::client_error::ClientError(match code.as_str() {
                        "C_SIGNATURE_INVALID" => "C_SIGNATURE_INVALID",
                        "C_AEAD_FAILURE" => "C_AEAD_FAILURE",
                        _ => "C_SCHEMA_VIOLATION",
                    })
                })?;
            (
                record.chain_id,
                record.device_id,
                record.collection_name,
                record.action,
                None,
                plaintext,
            )
        }
        SessionKeys::V21(keys) => {
            let record: crate::models::legacy::SyncRecordV21 = serde_json::from_value(record_value)
                .map_err(|_| crate::client_error::ClientError("C_SCHEMA_VIOLATION"))?;
            let plaintext =
                crate::crypto::decrypt_sync_record_v21_json(record_json, device_public_key, keys)
                    .map_err(|code| {
                    crate::client_error::ClientError(match code.as_str() {
                        "C_SIGNATURE_INVALID" => "C_SIGNATURE_INVALID",
                        "C_AEAD_FAILURE" => "C_AEAD_FAILURE",
                        _ => "C_SCHEMA_VIOLATION",
                    })
                })?;
            (
                record.chain_id,
                record.device_id,
                record.collection_name,
                record.action,
                Some((record.lamport, record.created_at_ms)),
                plaintext,
            )
        }
    };
    let keys_chain_id = match &*session.keys {
        SessionKeys::V20(keys) => &keys.chain_id,
        SessionKeys::V21(keys) => &keys.chain_id,
    };
    if source_device != expected_device_id || source_chain != *keys_chain_id {
        return Err(crate::client_error::ClientError(
            "C_BOOTSTRAP_TRUST_REQUIRED",
        ));
    }
    let candidates = match binding {
        Some((lamport, created_at_ms)) => convert_v21_data_payload_bound(
            &plaintext,
            &collection,
            &action,
            Some(lamport),
            Some(created_at_ms),
        ),
        None => convert_v21_data_payload(&plaintext, &collection, &action),
    }
    .map_err(|_| crate::client_error::ClientError("C_SCHEMA_VIOLATION"))?;
    let output = serde_jcs::to_vec(&candidates)
        .map_err(|_| crate::client_error::ClientError("C_SCHEMA_VIOLATION"))?;
    Ok(Zeroizing::new(output))
}

pub fn end(handle: u64) {
    if let Ok(mut all) = sessions().lock() {
        all.remove(&handle);
    }
}

pub fn end_all() {
    if let Ok(mut all) = sessions().lock() {
        all.clear();
    }
}

pub(crate) fn expire() {
    if let Some(all) = SESSIONS.get()
        && let Ok(mut all) = all.lock()
    {
        all.retain(|_, session| session.touched.elapsed() < IDLE && session.lease.live());
    }
}

fn invalid() -> String {
    "C_SCHEMA_VIOLATION".into()
}

fn exact_fields(value: &Value, required: &[&str], optional: &[&str]) -> Result<(), String> {
    let object = value.as_object().ok_or_else(invalid)?;
    if required.iter().any(|key| !object.contains_key(*key))
        || object
            .keys()
            .any(|key| !required.contains(&key.as_str()) && !optional.contains(&key.as_str()))
    {
        return Err(invalid());
    }
    Ok(())
}

fn normalize(value: &str) -> (String, bool) {
    let normalized = value.nfc().collect::<String>();
    let changed = normalized != value;
    (normalized, changed)
}

fn identifier_matches_number(entity: &str, body: &Value) -> bool {
    let Some(id) = body.get("id").and_then(Value::as_u64) else {
        return false;
    };
    id > 0 && id <= MAX_SAFE_INTEGER && entity == id.to_string()
}

fn canonical_safe_decimal(value: &str) -> bool {
    !value.is_empty()
        && !value.starts_with('0')
        && value.bytes().all(|b| b.is_ascii_digit())
        && value
            .parse::<u64>()
            .is_ok_and(|id| id > 0 && id <= MAX_SAFE_INTEGER)
}

fn legacy_id(value: &Value) -> Result<u64, String> {
    let id = if let Some(number) = value.as_u64() {
        number
    } else if let Some(decimal) = value.as_str() {
        if !canonical_safe_decimal(decimal) {
            return Err(invalid());
        }
        decimal.parse().map_err(|_| invalid())?
    } else {
        return Err(invalid());
    };
    if id == 0 || id > MAX_SAFE_INTEGER {
        return Err(invalid());
    }
    Ok(id)
}

fn v3_candidate(
    collection: &str,
    legacy_entity_id: String,
    entity_id: String,
    body: Value,
    normalization_changed: bool,
    normalization_collision: bool,
) -> Result<LegacyEntityCandidate, String> {
    let context = if collection == "palleria.favorite_tag/3" {
        json!({"order_key":"1"})
    } else {
        json!({})
    };
    let operation = json!({
        "entity_id": entity_id,
        "operation": "upsert",
        "lamport": 1,
        "created_at_ms": 1,
        "context": context,
        "body": body,
    });
    crate::schema::validate_operation(collection, &operation).map_err(|_| invalid())?;
    Ok(LegacyEntityCandidate {
        collection: collection.into(),
        legacy_entity_id,
        entity_id,
        operation: "upsert".into(),
        body,
        legacy_lamport: 0,
        legacy_created_at_ms: 0,
        normalization_changed,
        normalization_collision,
    })
}

fn append_deduplicated(
    output: &mut Vec<LegacyEntityCandidate>,
    seen: &mut std::collections::BTreeMap<String, String>,
    candidate: LegacyEntityCandidate,
) {
    if let Some(previous_source) = seen.get(&candidate.entity_id) {
        if previous_source != &candidate.legacy_entity_id
            && let Some(previous) = output
                .iter_mut()
                .find(|previous| previous.entity_id == candidate.entity_id)
        {
            previous.normalization_collision = true;
        }
        return;
    }
    seen.insert(
        candidate.entity_id.clone(),
        candidate.legacy_entity_id.clone(),
    );
    output.push(candidate);
}

fn normalize_view_body(body: &mut Value) -> Result<(bool, bool), String> {
    let object = body.as_object_mut().ok_or_else(invalid)?;
    let mut changed = false;
    let mut collision = false;
    for field in ["title", "artistName"] {
        if let Some(value) = object.get(field) {
            let text = value.as_str().ok_or_else(invalid)?;
            let (normalized, was_changed) = normalize(text);
            changed |= was_changed;
            object.insert(field.into(), Value::String(normalized));
        }
    }
    if let Some(tags) = object.get("tags") {
        let tags = tags.as_array().ok_or_else(invalid)?;
        let mut seen = BTreeSet::new();
        let mut normalized_tags = Vec::with_capacity(tags.len());
        for tag in tags {
            let tag = tag.as_str().ok_or_else(invalid)?;
            let (tag, was_changed) = normalize(tag);
            changed |= was_changed;
            if !seen.insert(tag.clone()) {
                collision = true;
                continue;
            }
            normalized_tags.push(Value::String(tag));
        }
        object.insert("tags".into(), Value::Array(normalized_tags));
    }
    Ok((changed, collision))
}

/// Convert a v2.1 `/1` replacement snapshot without inferring deletes from absent entries.
/// Search/view arrays are most-recent-first in the legacy format, so their candidates are
/// returned oldest-first for fresh increasing v3 access stamps (§9.2). Favorites retain list order.
pub fn convert_v21_snapshot_payload(
    collection: &str,
    body: &Value,
) -> Result<Vec<LegacyEntityCandidate>, String> {
    let mut output: Vec<LegacyEntityCandidate> = Vec::new();
    match collection {
        "palleria.favorite_tag/1" | "palleria.search_history/1" => {
            let values = body.as_array().ok_or_else(invalid)?;
            let target = if collection == "palleria.favorite_tag/1" {
                "palleria.favorite_tag/3"
            } else {
                "palleria.search_history/3"
            };
            let mut seen = std::collections::BTreeMap::new();
            for value in values {
                let raw = value.as_str().ok_or_else(invalid)?;
                let (normalized, changed) = normalize(raw);
                let member = if target == "palleria.favorite_tag/3" {
                    "tag"
                } else {
                    "query"
                };
                let candidate = v3_candidate(
                    target,
                    raw.into(),
                    normalized.clone(),
                    json!({member:normalized}),
                    changed,
                    false,
                )?;
                append_deduplicated(&mut output, &mut seen, candidate);
            }
            if target == "palleria.search_history/3" {
                output.reverse();
            }
        }
        "palleria.mute_settings/1" => {
            exact_fields(body, &["mutedTags", "mutedUsers", "mutedIllusts"], &[])?;
            let mut seen = std::collections::BTreeMap::new();
            for (field, kind) in [
                ("mutedTags", "tag"),
                ("mutedUsers", "user"),
                ("mutedIllusts", "illust"),
            ] {
                let values = body[field].as_array().ok_or_else(invalid)?;
                for value in values {
                    let raw = value.as_str().ok_or_else(invalid)?;
                    let (value, changed) = if kind == "tag" {
                        normalize(raw)
                    } else {
                        (raw.to_owned(), false)
                    };
                    let key = format!("{kind}:{value}");
                    let candidate = v3_candidate(
                        "palleria.mute_settings/3",
                        raw.into(),
                        key,
                        json!({"kind":kind,"value":value}),
                        changed,
                        false,
                    )?;
                    append_deduplicated(&mut output, &mut seen, candidate);
                }
            }
        }
        "palleria.view_history/1" => {
            exact_fields(body, &["seenFeedIllusts", "viewedIllusts"], &[])?;
            let mut seen = std::collections::BTreeMap::new();
            for value in body["seenFeedIllusts"].as_array().ok_or_else(invalid)? {
                let id = legacy_id(value)?;
                let text = id.to_string();
                let candidate = v3_candidate(
                    "palleria.view_history/3",
                    value.to_string(),
                    format!("seen:{text}"),
                    json!({"id":id}),
                    false,
                    false,
                )?;
                append_deduplicated(&mut output, &mut seen, candidate);
            }
            let mut viewed = Vec::new();
            for value in body["viewedIllusts"].as_array().ok_or_else(invalid)? {
                let mut converted = value.as_object().cloned().ok_or_else(invalid)?;
                let source_id = converted.get("id").cloned().ok_or_else(invalid)?;
                let id = legacy_id(&source_id)?;
                converted.insert("id".into(), json!(id));
                if let Some(page_count) = converted.get("pageCount").cloned() {
                    let count = legacy_id(&page_count)?;
                    if count > 1_000_000 {
                        return Err(invalid());
                    }
                    converted.insert("pageCount".into(), json!(count));
                }
                let original: String = serde_json::to_string(value).map_err(|_| invalid())?;
                let mut normalized_body = Value::Object(converted);
                let (changed, collision) = normalize_view_body(&mut normalized_body)?;
                let text = id.to_string();
                let candidate = v3_candidate(
                    "palleria.view_history/3",
                    original,
                    format!("viewed:{text}"),
                    normalized_body,
                    changed,
                    collision,
                )?;
                append_deduplicated(&mut viewed, &mut seen, candidate);
            }
            viewed.reverse();
            output.extend(viewed);
        }
        _ => return Err(invalid()),
    }
    Ok(output)
}

/// Route one authenticated decrypted DataPayload by its signed collection/action envelope.
/// A `/1` replacement produces many operation candidates; `/2` produces one event candidate.
pub fn convert_v21_data_payload(
    plaintext: &[u8],
    envelope_collection: &str,
    envelope_action: &str,
) -> Result<Vec<LegacyEntityCandidate>, String> {
    convert_v21_data_payload_bound(plaintext, envelope_collection, envelope_action, None, None)
}

/// Variant used by the authenticated v2.1 session: duplicated payload stamps must match the
/// signed envelope, while v2.0 has no such envelope fields (§9.2 and v2.1 §7.1/§8).
pub fn convert_v21_data_payload_bound(
    plaintext: &[u8],
    envelope_collection: &str,
    envelope_action: &str,
    envelope_lamport: Option<i64>,
    envelope_created_at_ms: Option<i64>,
) -> Result<Vec<LegacyEntityCandidate>, String> {
    let payload: DataPayloadV21 = serde_json::from_slice(plaintext).map_err(|_| invalid())?;
    if payload.schema != envelope_collection
        || payload.operation != envelope_action
        || payload.created_at_ms < 0
        || payload.lamport > crate::strict::jcs::MAX_INTEGER
        || envelope_lamport.is_some_and(|value| value < 0 || payload.lamport != value as u64)
        || envelope_created_at_ms.is_some_and(|value| payload.created_at_ms != value)
        || !payload.context.as_object().is_some_and(Map::is_empty)
        || payload.entity_id.is_empty()
    {
        return Err(invalid());
    }
    if payload.schema.ends_with("/1") {
        if payload.operation != "upsert" {
            return Err(invalid());
        }
        return convert_v21_snapshot_payload(&payload.schema, &payload.body);
    }
    Ok(vec![convert_v21_payload(
        plaintext,
        envelope_collection,
        envelope_action,
    )?])
}

/// Convert one already authenticated and decrypted v2.1 `/2` payload.
/// The caller must resolve all source events according to the legacy conflict policy,
/// retain the immutable export, and assign fresh increasing v3 Lamports before authoring.
pub fn convert_v21_payload(
    plaintext: &[u8],
    envelope_collection: &str,
    envelope_action: &str,
) -> Result<LegacyEntityCandidate, String> {
    if plaintext.len() > 8 * 1024 * 1024 {
        return Err(invalid());
    }
    let payload: DataPayloadV21 = serde_json::from_slice(plaintext).map_err(|_| invalid())?;
    let legacy_entity_id = payload.entity_id.clone();
    if payload.lamport > crate::strict::jcs::MAX_INTEGER
        || payload.created_at_ms < 0
        || !matches!(payload.operation.as_str(), "upsert" | "delete")
        || payload.operation != envelope_action
        || !payload.context.as_object().is_some_and(Map::is_empty)
        || payload.entity_id.is_empty()
    {
        return Err(invalid());
    }

    let mut body_normalization_changed = false;
    let mut normalization_collision = false;
    let (collection, entity_id, body, normalization_changed) = match payload.schema.as_str() {
        "palleria.favorite_tag/2" if envelope_collection == "palleria.favorite_tag/2" => {
            if payload.operation == "delete" {
                exact_fields(&payload.body, &[], &[])?;
                let (id, changed) = normalize(&payload.entity_id);
                ("palleria.favorite_tag/3", id, json!({}), changed)
            } else {
                exact_fields(&payload.body, &["tag"], &[])?;
                let tag = payload.body["tag"].as_str().ok_or_else(invalid)?;
                let (tag, changed) = normalize(tag);
                let (id, id_changed) = normalize(&payload.entity_id);
                if id != tag || tag.trim().is_empty() {
                    return Err(invalid());
                }
                (
                    "palleria.favorite_tag/3",
                    id,
                    json!({"tag": tag}),
                    changed || id_changed,
                )
            }
        }
        "palleria.search_history/2" if envelope_collection == "palleria.search_history/2" => {
            if payload.operation == "delete" {
                exact_fields(&payload.body, &[], &[])?;
                let (id, changed) = normalize(&payload.entity_id);
                ("palleria.search_history/3", id, json!({}), changed)
            } else {
                exact_fields(&payload.body, &["query"], &[])?;
                let query = payload.body["query"].as_str().ok_or_else(invalid)?;
                let (query, changed) = normalize(query);
                let (id, id_changed) = normalize(&payload.entity_id);
                if query != id {
                    return Err(invalid());
                }
                (
                    "palleria.search_history/3",
                    id,
                    json!({"query": query}),
                    changed || id_changed,
                )
            }
        }
        "palleria.mute_settings/2" if envelope_collection == "palleria.mute_settings/2" => {
            if payload.operation == "delete" {
                exact_fields(&payload.body, &[], &[])?;
                let (id, changed) = normalize(&payload.entity_id);
                ("palleria.mute_settings/3", id, json!({}), changed)
            } else {
                exact_fields(&payload.body, &["kind", "value"], &[])?;
                let kind = payload.body["kind"].as_str().ok_or_else(invalid)?;
                let value = payload.body["value"].as_str().ok_or_else(invalid)?;
                if !matches!(kind, "tag" | "user" | "illust") {
                    return Err(invalid());
                }
                let (value, changed) = if kind == "tag" {
                    normalize(value)
                } else {
                    (value.to_owned(), false)
                };
                if value.is_empty()
                    || (kind != "tag"
                        && (!value.bytes().all(|b| b.is_ascii_digit()) || value.starts_with('0')))
                {
                    return Err(invalid());
                }
                let expected_id = format!("{kind}:{value}");
                let (id, id_changed) = normalize(&payload.entity_id);
                if id != expected_id {
                    return Err(invalid());
                }
                (
                    "palleria.mute_settings/3",
                    id,
                    json!({"kind":kind,"value":value}),
                    changed || id_changed,
                )
            }
        }
        "palleria.view_history/2" if envelope_collection == "palleria.view_history/2" => {
            let (scope, legacy_id) = payload.entity_id.split_once(':').ok_or_else(invalid)?;
            if !matches!(scope, "seen" | "viewed") || !canonical_safe_decimal(legacy_id) {
                return Err(invalid());
            }
            if payload.operation == "delete" {
                exact_fields(&payload.body, &[], &[])?;
                (
                    "palleria.view_history/3",
                    payload.entity_id.clone(),
                    json!({}),
                    false,
                )
            } else {
                exact_fields(
                    &payload.body,
                    &["id"],
                    if scope == "viewed" {
                        &[
                            "title",
                            "artistName",
                            "imageUrl",
                            "pageCount",
                            "type",
                            "isBookmarked",
                            "xRestrict",
                            "illustAiType",
                            "tags",
                        ]
                    } else {
                        &[]
                    },
                )?;
                if !identifier_matches_number(legacy_id, &payload.body) {
                    return Err(invalid());
                }
                let mut body = payload.body.clone();
                if scope == "viewed" {
                    if let Some(title) = body.get("title").and_then(Value::as_str) {
                        let (normalized, changed) = normalize(title);
                        body_normalization_changed |= changed;
                        body["title"] = Value::String(normalized);
                    }
                    if let Some(artist) = body.get("artistName").and_then(Value::as_str) {
                        let (normalized, changed) = normalize(artist);
                        body_normalization_changed |= changed;
                        body["artistName"] = Value::String(normalized);
                    }
                    if let Some(tags) = body.get("tags") {
                        let tags = tags.as_array().ok_or_else(invalid)?;
                        let mut converted = Vec::with_capacity(tags.len());
                        let mut seen = BTreeSet::new();
                        for tag in tags {
                            let tag = tag.as_str().ok_or_else(invalid)?;
                            let (tag, changed) = normalize(tag);
                            body_normalization_changed |= changed;
                            if !seen.insert(tag.clone()) {
                                normalization_collision = true;
                                continue;
                            }
                            converted.push(Value::String(tag));
                        }
                        body["tags"] = Value::Array(converted);
                    }
                }
                (
                    "palleria.view_history/3",
                    payload.entity_id.clone(),
                    body,
                    false,
                )
            }
        }
        _ => return Err(invalid()),
    };

    // Validate the converted body with the v3 collection validator before it can be
    // recorded in the resume journal. The temporary stamp/order key are placeholders
    // used only for shape checking and are never returned as destination authority.
    let normalization_changed = normalization_changed || body_normalization_changed;
    let context = if collection == "palleria.favorite_tag/3" && payload.operation == "upsert" {
        json!({"order_key":"1"})
    } else {
        json!({})
    };
    let validation_operation = json!({
        "entity_id": entity_id,
        "operation": payload.operation,
        "lamport": 1,
        "created_at_ms": 1,
        "context": context,
        "body": body,
    });
    crate::schema::validate_operation(collection, &validation_operation).map_err(|_| invalid())?;

    Ok(LegacyEntityCandidate {
        collection: collection.into(),
        legacy_entity_id,
        entity_id,
        operation: payload.operation,
        body,
        legacy_lamport: payload.lamport,
        legacy_created_at_ms: payload.created_at_ms,
        normalization_changed,
        normalization_collision,
    })
}
