//! §16.6 streaming state validation and §7.8 commitment. One entry at a time;
//! callers write validated entries to staging and publish only after finish succeeds.
use crate::{
    client_error::{ClientError, Result},
    public_wire::{self as wire, fields, n, s},
    schema,
    strict::{jcs, verify::decode},
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use hmac::{Hmac, Mac};
use serde_json::{Value, json};
use sha2::Sha256;
use std::{
    collections::HashMap,
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicU64, Ordering},
    },
    time::{Duration, Instant},
};
use subtle::ConstantTimeEq;

fn invalid() -> ClientError {
    ClientError("C_SNAPSHOT_INVALID")
}
fn require(ok: bool) -> Result {
    if ok { Ok(()) } else { Err(invalid()) }
}
#[derive(Eq, PartialEq, Ord, PartialOrd)]
enum Identity {
    Header,
    Entity(Vec<u8>, Vec<u8>),
    Clear(Vec<u8>, Vec<u8>),
    Retention(Vec<u8>, Vec<u8>),
    Author(u32, [u8; 16]),
    Gap(u32, [u8; 16], u64),
    Projection(Vec<u8>, Vec<u8>),
}
fn uuid(v: &Value) -> Result<[u8; 16]> {
    let text = v.as_str().ok_or_else(invalid)?;
    require(wire::uuid(text))?;
    Ok(*uuid::Uuid::parse_str(text)
        .map_err(|_| invalid())?
        .as_bytes())
}
fn key(value: &Value) -> Result {
    let k = value.as_array().ok_or_else(invalid)?;
    require(
        k.len() == 5
            && wire::check_type(&k[0], "int")
            && k[1].as_u64().is_some_and(|n| n <= 1)
            && k[4].as_u64().is_some_and(|n| n <= 1023),
    )?;
    uuid(&k[2])?;
    uuid(&k[3])?;
    Ok(())
}
fn scope(collection: &str, value: &str, retention: bool) -> bool {
    if collection == "palleria.search_history/3" {
        value == "all"
    } else if collection == "palleria.view_history/3" {
        matches!(value, "seen" | "viewed") || (!retention && value == "all")
    } else {
        !retention
            && matches!(
                collection,
                "palleria.favorite_tag/3" | "palleria.mute_settings/3"
            )
            && value == "all"
    }
}
fn validate_entry(value: &Value) -> Result<Identity> {
    crate::normal_data_boundary::check(value).map_err(|_| invalid())?;
    let collection = s(value, "collection").as_bytes().to_vec();
    let scoped = s(value, "scope").as_bytes().to_vec();
    let identity = match s(value, "kind") {
        "header" => {
            fields(
                value,
                &[
                    ("kind", "header"),
                    ("chain_id", "b32"),
                    ("generation", "u32"),
                    ("log_id", "uuid"),
                    ("covered_through_seq", "int"),
                    ("covered_log_hash", "b32"),
                    ("epoch_hash", "b32"),
                    ("lamport_max", "int"),
                ],
                &[],
            )
            .map_err(|_| invalid())?;
            Identity::Header
        }
        "entity" => {
            fields(
                value,
                &[
                    ("kind", "entity"),
                    ("collection", "string"),
                    ("entity_id", "string"),
                    ("key", "array"),
                    ("operation", "upsert|delete"),
                    ("body", "object"),
                    ("context", "object"),
                ],
                &[],
            )
            .map_err(|_| invalid())?;
            key(&value["key"])?;
            require(
                value["key"][1]
                    == if value["operation"] == "delete" {
                        json!(1)
                    } else {
                        json!(0)
                    },
            )?;
            require(s(value, "entity_id") != "*")?;
            let operation = json!({"entity_id":value["entity_id"],"operation":value["operation"],"lamport":value["key"][0],"created_at_ms":0,"context":value["context"],"body":value["body"]});
            schema::validate_inner(&json!({"collection":value["collection"],"device_seq":1,"prev_record_hash":null,"operations":[operation]})).map_err(|_|invalid())?;
            Identity::Entity(collection, s(value, "entity_id").as_bytes().to_vec())
        }
        "clear" => {
            fields(
                value,
                &[
                    ("kind", "clear"),
                    ("collection", "string"),
                    ("scope", "string"),
                    ("key", "array"),
                ],
                &[],
            )
            .map_err(|_| invalid())?;
            key(&value["key"])?;
            require(
                value["key"][1] == 1 && scope(s(value, "collection"), s(value, "scope"), false),
            )?;
            Identity::Clear(collection, scoped)
        }
        "retention" => {
            fields(
                value,
                &[
                    ("kind", "retention"),
                    ("collection", "string"),
                    ("scope", "string"),
                    ("key", "array"),
                    ("limit", "int?"),
                    ("stop_on_safety_limit", "bool"),
                ],
                &[],
            )
            .map_err(|_| invalid())?;
            key(&value["key"])?;
            require(
                value["key"][1] == 0 && scope(s(value, "collection"), s(value, "scope"), true),
            )?;
            let operation = json!({"entity_id":"*","operation":"upsert","lamport":value["key"][0],"created_at_ms":0,"context":{"scope":"retention"},"body":{"scope":value["scope"],"limit":value["limit"],"stop_on_safety_limit":value["stop_on_safety_limit"]}});
            schema::validate_inner(&json!({"collection":value["collection"],"device_seq":1,"prev_record_hash":null,"operations":[operation]})).map_err(|_|invalid())?;
            Identity::Retention(collection, scoped)
        }
        "author" => {
            fields(
                value,
                &[
                    ("kind", "author"),
                    ("generation", "u32"),
                    ("device_id", "uuid"),
                    ("device_seq", "int"),
                    ("record_hash", "b32"),
                ],
                &[],
            )
            .map_err(|_| invalid())?;
            require(n(value, "device_seq") > 0)?;
            Identity::Author(n(value, "generation") as u32, uuid(&value["device_id"])?)
        }
        "author_gap" => {
            fields(
                value,
                &[
                    ("kind", "author_gap"),
                    ("generation", "u32"),
                    ("device_id", "uuid"),
                    ("first", "int"),
                    ("last", "int"),
                ],
                &[],
            )
            .map_err(|_| invalid())?;
            require(n(value, "first") > 0 && n(value, "first") <= n(value, "last"))?;
            Identity::Gap(
                n(value, "generation") as u32,
                uuid(&value["device_id"])?,
                n(value, "first"),
            )
        }
        "projection" => {
            fields(
                value,
                &[
                    ("kind", "projection"),
                    ("collection", "string"),
                    ("scope", "string"),
                    ("limit", "int"),
                    ("eviction_floor", "object?"),
                ],
                &[],
            )
            .map_err(|_| invalid())?;
            require(scope(s(value, "collection"), s(value, "scope"), true))?;
            let ceiling = if value["scope"] == "seen" {
                10_000_000
            } else {
                1_000_000
            };
            require(n(value, "limit") > 0 && n(value, "limit") <= ceiling)?;
            if !value["eviction_floor"].is_null() {
                fields(
                    &value["eviction_floor"],
                    &[("key", "array"), ("entity_id", "string")],
                    &[],
                )
                .map_err(|_| invalid())?;
                key(&value["eviction_floor"]["key"])?;
                require((1..=4096).contains(&s(&value["eviction_floor"], "entity_id").len()))?;
            }
            Identity::Projection(collection, scoped)
        }
        _ => return Err(invalid()),
    };
    Ok(identity)
}

/// Validate the public frame independently of prefix-wide ordering and commitment state.
/// This is used at the reserved snapshot decryption boundary before a part is exposed.
pub fn validate_part_frame(part: &Value) -> Result {
    fields(
        part,
        &[
            ("type", "PALLASYNC-SNAPSHOT-v3"),
            ("collection", "pallasync.snapshot/3"),
            ("compaction_id", "uuid"),
            ("part_index", "u32"),
            ("part_count", "u32"),
            ("entries", "array"),
        ],
        &[],
    )
    .map_err(|_| invalid())?;
    require(
        (1..=65_536).contains(&n(part, "part_count"))
            && n(part, "part_index") < n(part, "part_count")
            && jcs::canonicalize_json(part).map_err(|_| invalid())?.len() <= 1_048_572,
    )
}
pub struct StateStream {
    mac: Hmac<Sha256>,
    previous: Option<Identity>,
    header: Value,
    previous_gap_last: u64,
    expected_part: u32,
    part_count: u32,
    failed: bool,
}

struct VerifierSession {
    stream: StateStream,
    compaction_id: String,
    last_activity: Instant,
}

static VERIFIERS: OnceLock<Mutex<HashMap<u64, VerifierSession>>> = OnceLock::new();
static NEXT_VERIFIER: AtomicU64 = AtomicU64::new(1);

fn verifiers() -> &'static Mutex<HashMap<u64, VerifierSession>> {
    VERIFIERS.get_or_init(|| Mutex::new(HashMap::new()))
}

/// Begin a short-lived native snapshot verification session (§16.6). The key is copied
/// only into the streaming HMAC state and is never returned to the managed runtime.
pub fn begin_verification(
    record_key: &[u8],
    header: &Value,
    compaction_id: &str,
    part_count: u32,
) -> Result<u64> {
    crate::secret_lifetime::start();
    require(record_key.len() == 32 && wire::uuid(compaction_id))?;
    let key: &[u8; 32] = record_key.try_into().map_err(|_| invalid())?;
    let stream = StateStream::new(key, header, part_count)?;
    let handle = NEXT_VERIFIER.fetch_add(1, Ordering::Relaxed);
    require(handle != 0)?;
    let mut sessions = verifiers().lock().map_err(|_| invalid())?;
    require(sessions.len() < 8)?;
    sessions.insert(
        handle,
        VerifierSession {
            stream,
            compaction_id: compaction_id.to_owned(),
            last_activity: Instant::now(),
        },
    );
    Ok(handle)
}

/// Add one already decrypted and independently authenticated snapshot part.
pub fn verification_part(handle: u64, part: &Value) -> Result {
    let mut sessions = verifiers().lock().map_err(|_| invalid())?;
    let session = sessions.get_mut(&handle).ok_or_else(invalid)?;
    if session.last_activity.elapsed() > Duration::from_secs(60) {
        sessions.remove(&handle);
        return Err(invalid());
    }
    let result = session.stream.part(part, &session.compaction_id);
    if result.is_err() {
        sessions.remove(&handle);
    } else {
        session.last_activity = Instant::now();
    }
    result
}

/// Finish exactly once. A failed/incomplete commitment destroys the session.
pub fn finish_verification(handle: u64, commitment: &str) -> Result {
    let session = verifiers()
        .lock()
        .map_err(|_| invalid())?
        .remove(&handle)
        .ok_or_else(invalid)?;
    require(session.last_activity.elapsed() <= Duration::from_secs(60))?;
    session.stream.finish(commitment)
}

/// Finish a fully validated stream and return its keyed commitment for a local snapshot producer.
/// This consumes the same one-shot session as verification and never exposes the record key.
pub fn finish_commitment(handle: u64) -> Result<String> {
    let session = verifiers()
        .lock()
        .map_err(|_| invalid())?
        .remove(&handle)
        .ok_or_else(invalid)?;
    require(session.last_activity.elapsed() <= Duration::from_secs(60))?;
    session.stream.commitment()
}

pub fn abort_verification(handle: u64) {
    if let Ok(mut sessions) = verifiers().lock() {
        sessions.remove(&handle);
    }
}

pub fn expire_verifications() {
    if let Ok(mut sessions) = verifiers().lock() {
        sessions.retain(|_, session| session.last_activity.elapsed() <= Duration::from_secs(60));
    }
}

pub fn abort_all_verifications() {
    if let Ok(mut sessions) = verifiers().lock() {
        sessions.clear();
    }
}
impl StateStream {
    pub fn new(record_key: &[u8; 32], expected_header: &Value, part_count: u32) -> Result<Self> {
        require(
            (1..=65536).contains(&part_count)
                && validate_entry(expected_header)? == Identity::Header,
        )?;
        let mut mac = Hmac::<Sha256>::new_from_slice(record_key).map_err(|_| invalid())?;
        mac.update(b"[");
        Ok(Self {
            mac,
            previous: None,
            header: expected_header.clone(),
            previous_gap_last: 0,
            expected_part: 0,
            part_count,
            failed: false,
        })
    }
    fn entry(&mut self, entry: &Value) -> Result {
        let identity = validate_entry(entry)?;
        if let Some(previous) = &self.previous {
            require(previous < &identity)?;
            if let (Identity::Gap(g, d, _), Identity::Gap(ng, nd, first)) = (previous, &identity)
                && g == ng
                && d == nd
            {
                require(self.previous_gap_last < *first)?;
            }
            self.mac.update(b",");
        } else {
            require(identity == Identity::Header && entry == &self.header)?;
        }
        if let Identity::Gap(_, _, _) = identity {
            self.previous_gap_last = n(entry, "last");
        }
        if let Some(k) = entry.get("key") {
            require(k[0].as_u64().ok_or_else(invalid)? <= n(&self.header, "lamport_max"))?;
        }
        let bytes = jcs::canonicalize_json(entry).map_err(|_| invalid())?;
        // Every individual entry must fit a part; no incomplete multi-snapshot workaround.
        require(bytes.len() < 1_048_576)?;
        self.mac.update(&bytes);
        self.previous = Some(identity);
        Ok(())
    }
    /// Input is a completely validated decrypted snapshot frame. An error aborts this
    /// staging session; never continue with this value or partially install its entries.
    pub fn part(&mut self, part: &Value, compaction_id: &str) -> Result {
        require(!self.failed)?;
        let result = self.part_inner(part, compaction_id);
        if result.is_err() {
            self.failed = true;
        }
        result
    }
    fn part_inner(&mut self, part: &Value, compaction_id: &str) -> Result {
        validate_part_frame(part)?;
        require(
            s(part, "compaction_id") == compaction_id
                && n(part, "part_index") == u64::from(self.expected_part)
                && n(part, "part_count") == u64::from(self.part_count)
                && self.expected_part < self.part_count,
        )?;
        for entry in wire::a(part, "entries") {
            self.entry(entry)?;
        }
        self.expected_part += 1;
        Ok(())
    }
    pub fn finish(mut self, expected_commitment: &str) -> Result {
        require(!self.failed && self.previous.is_some() && self.expected_part == self.part_count)?;
        self.mac.update(b"]");
        let computed = self.mac.finalize().into_bytes();
        let expected = decode(expected_commitment, Some(32)).map_err(|_| invalid())?;
        require(bool::from(computed.as_slice().ct_eq(&expected)))
    }
    pub fn commitment(mut self) -> Result<String> {
        require(!self.failed && self.previous.is_some() && self.expected_part == self.part_count)?;
        self.mac.update(b"]");
        Ok(URL_SAFE_NO_PAD.encode(self.mac.finalize().into_bytes()))
    }
}
