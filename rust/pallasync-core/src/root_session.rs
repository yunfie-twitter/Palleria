//! §5.5: root input has a named-operation lifetime; handles never serialize secrets.
use crate::{
    client_error::{ClientError, Result},
    crypto::RootKeys,
    locked_memory::{Locked, LockedBytes},
    recovery::{self, RecoveryInput, SecretJson},
    shamir::{self, Piece},
    strict::verify::object_hash,
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use chacha20poly1305::{
    KeyInit, XChaCha20Poly1305, XNonce,
    aead::{Aead, Payload},
};
use rand::RngCore;
use serde_json::{Value, json};
use std::{
    collections::{BTreeSet, HashMap},
    sync::{Mutex, OnceLock},
    time::{Duration, Instant},
};
use zeroize::Zeroize;
use zeroize::Zeroizing;

const IDLE_LIFETIME: Duration = Duration::from_secs(600);
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Operation {
    Genesis,
    Enrollment,
    Rotate,
    Revoke,
    Reseed,
    Delete,
    Prune,
    RecoveryCreate,
    RecoveryRotate,
    RecoveryDisable,
    NewRootMigration,
}
impl Operation {
    pub fn parse(v: &str) -> Result<Self> {
        Ok(match v {
            "genesis" => Self::Genesis,
            "enrollment" => Self::Enrollment,
            "rotate" => Self::Rotate,
            "revoke" => Self::Revoke,
            "reseed" => Self::Reseed,
            "delete" => Self::Delete,
            "prune" => Self::Prune,
            "recovery_create" => Self::RecoveryCreate,
            "recovery_rotate" => Self::RecoveryRotate,
            "recovery_disable" => Self::RecoveryDisable,
            "new_root_migration" => Self::NewRootMigration,
            _ => return Err(ClientError("C_RECOVERY_AUTH_REQUIRED")),
        })
    }
}
pub struct PreparedSet {
    pub manifest: Value,
    pub blob: Value,
    pub statuses: Vec<Value>,
    pieces: Vec<Locked<Piece>>,
    proposals: HashMap<u8, Value>,
    acknowledged: BTreeSet<u8>,
    threshold_test_passed: bool,
}
pub struct RootSession {
    lease: crate::secret_lifetime::Lease,
    input: Option<ProtectedRecoveryInput>,
    pub(crate) roots: Locked<RootKeys>,
    operation: Operation,
    last_interaction: Instant,
    pub(crate) prepared: Option<PreparedSet>,
    recovery_source_set: Option<String>,
    active_replacement: Option<String>,
}
struct ProtectedRecoveryInput {
    mnemonic: LockedBytes,
    passphrase: LockedBytes,
    fingerprint: String,
}
impl ProtectedRecoveryInput {
    fn new(input: RecoveryInput) -> Result<Self> {
        Ok(Self {
            fingerprint: input.fingerprint()?,
            mnemonic: LockedBytes::new(input.mnemonic.as_bytes())?,
            passphrase: LockedBytes::new(input.passphrase.as_bytes())?,
        })
        // RecoveryInput's Drop overwrites the original temporary String buffers.
    }
    fn mnemonic(&self) -> Result<&str> {
        std::str::from_utf8(self.mnemonic.as_slice())
            .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))
    }
    fn passphrase(&self) -> Result<&str> {
        std::str::from_utf8(self.passphrase.as_slice())
            .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))
    }
}
static SESSIONS: OnceLock<Mutex<HashMap<u64, RootSession>>> = OnceLock::new();
fn sessions() -> &'static Mutex<HashMap<u64, RootSession>> {
    SESSIONS.get_or_init(|| Mutex::new(HashMap::new()))
}

pub fn begin(
    mnemonic: &[u8],
    passphrase: &[u8],
    operation: Operation,
    expected_chain: Option<&str>,
    expected_admin: Option<&str>,
) -> Result<u64> {
    let lease = crate::secret_lifetime::lease()?;
    let input = RecoveryInput::from_user(
        std::str::from_utf8(mnemonic).map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?,
        std::str::from_utf8(passphrase).map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?,
    )?;
    insert_input_at(
        input,
        operation,
        expected_chain,
        expected_admin,
        lease,
        None,
    )
}
pub(crate) fn insert_input_at(
    input: RecoveryInput,
    operation: Operation,
    expected_chain: Option<&str>,
    expected_admin: Option<&str>,
    lease: crate::secret_lifetime::Lease,
    recovery_source_set: Option<&str>,
) -> Result<u64> {
    crate::secret_lifetime::start();
    lease.check()?;
    let mut roots = input.roots()?;
    // Session operations use the inline bytes; discard the legacy encoded heap copy immediately.
    roots.chain_salt.zeroize();
    if expected_chain.is_some_and(|c| c != roots.chain_id)
        || expected_admin.is_some_and(|k| k != roots.admin_public_key)
    {
        return Err(ClientError("C_RECOVERY_ROOT_MISMATCH"));
    }
    // Only set provisioning needs root inputs after derivation; other named operations discard them now.
    let input = if matches!(
        operation,
        Operation::RecoveryCreate | Operation::RecoveryRotate
    ) {
        Some(ProtectedRecoveryInput::new(input)?)
    } else {
        drop(input);
        None
    };
    let mut map = sessions()
        .lock()
        .map_err(|_| ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
    lease.check()?;
    map.retain(|_, s| s.lease.live() && s.last_interaction.elapsed() < IDLE_LIFETIME);
    let mut handle = rand::random::<u64>() & i64::MAX as u64;
    while handle == 0 || map.contains_key(&handle) {
        handle = rand::random::<u64>() & i64::MAX as u64;
    }
    map.insert(
        handle,
        RootSession {
            lease,
            input,
            roots: Locked::new(roots)?,
            operation,
            last_interaction: Instant::now(),
            prepared: None,
            active_replacement: None,
            recovery_source_set: recovery_source_set.map(str::to_owned),
        },
    );
    Ok(handle)
}
pub fn end(handle: u64) {
    if let Ok(mut map) = sessions().lock() {
        map.remove(&handle);
    }
}
pub fn end_all() {
    if let Ok(mut map) = sessions().lock() {
        map.clear();
    }
}
pub(crate) fn invalidate_recovery_set(set_id: &str) {
    if let Ok(mut map) = sessions().lock() {
        map.retain(|_, session| {
            session.recovery_source_set.as_deref() != Some(set_id)
                && session
                    .prepared
                    .as_ref()
                    .is_none_or(|set| set.manifest["recovery_set_id"] != set_id)
        });
    }
}
pub(crate) fn expire() {
    if let Ok(mut map) = sessions().lock() {
        map.retain(|_, s| s.lease.live() && s.last_interaction.elapsed() < IDLE_LIFETIME);
    }
}
pub(crate) fn with<T>(
    handle: u64,
    operation: Operation,
    f: impl FnOnce(&mut RootSession) -> Result<T>,
) -> Result<T> {
    let mut map = sessions()
        .lock()
        .map_err(|_| ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
    map.retain(|_, s| s.lease.live() && s.last_interaction.elapsed() < IDLE_LIFETIME);
    let session = map
        .get_mut(&handle)
        .ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
    if session.operation != operation {
        return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
    }
    let result = f(session);
    session.lease.check()?;
    result
}
pub fn touch(handle: u64) -> Result {
    let mut map = sessions()
        .lock()
        .map_err(|_| ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
    let session = map
        .get_mut(&handle)
        .ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
    if !session.lease.live() || session.last_interaction.elapsed() >= IDLE_LIFETIME {
        map.remove(&handle);
        return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
    }
    session.last_interaction = Instant::now();
    Ok(())
}
pub fn identity(handle: u64) -> Result<Value> {
    let mut map = sessions()
        .lock()
        .map_err(|_| ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
    map.retain(|_, s| s.lease.live() && s.last_interaction.elapsed() < IDLE_LIFETIME);
    let session = map
        .get(&handle)
        .ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
    Ok(json!({"chain_id":session.roots.chain_id,"admin_public_key":session.roots.admin_public_key}))
}

/// Build the specification-bound administrator request for §10.15 deletion.
/// The root key remains inside the named native session and only the signed
/// public request body crosses JNI.
pub fn delete_request(
    handle: u64,
    generation: u32,
    deletion_id: &str,
    expected_parameters_hash: &str,
    expected_epoch_hash: &str,
    created_at_ms: i64,
) -> Result<String> {
    with(handle, Operation::Delete, |session| {
        let payload = json!({
            "deletion_id": deletion_id,
            "expected_parameters_hash": expected_parameters_hash,
            "expected_epoch_hash": expected_epoch_hash,
        });
        let target_hash = crate::strict::verify::object_hash(&payload);
        let mut operation = json!({
            "operation_id": uuid::Uuid::new_v4().to_string(),
            "chain_id": session.roots.chain_id,
            "generation": generation,
            "action": "delete_chain",
            "expected_parameters_hash": expected_parameters_hash,
            "expected_epoch_hash": expected_epoch_hash,
            "target_hash": target_hash,
            "created_at_ms": created_at_ms,
            "signature": "",
        });
        let canonical = crate::models::unsigned_record_jcs(&operation)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        operation["signature"] = serde_json::Value::String(crate::crypto::sign_with_context(
            &session.roots.admin_key,
            crate::crypto::CTX_ADMIN_OP,
            &canonical,
        ));
        if !crate::public_wire::check_type(&operation["expected_parameters_hash"], "b32")
            || !crate::public_wire::check_type(&operation["expected_epoch_hash"], "b32")
        {
            return Err(ClientError("C_SCHEMA_VIOLATION"));
        }
        if !crate::public_wire::uuid(deletion_id) {
            return Err(ClientError("C_SCHEMA_VIOLATION"));
        }
        let body = json!({
            "deletion_id": deletion_id,
            "expected_parameters_hash": expected_parameters_hash,
            "expected_epoch_hash": expected_epoch_hash,
            "admin_operation": operation,
        });
        serde_json::to_string(&body).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    })
}

fn canonical_number(value: &str, maximum: u64, positive: bool) -> bool {
    value
        .parse::<u64>()
        .is_ok_and(|n| n <= maximum && (!positive || n > 0) && n.to_string() == value)
}
fn catalogue_read(tail: &str) -> bool {
    matches!(
        tail,
        "/parameters"
            | "/epochs"
            | "/devices"
            | "/records"
            | "/enrollment-requests"
            | "/log"
            | "/log/head"
            | "/transparency/entries"
            | "/transparency/head"
            | "/snapshots"
            | "/export"
            | "/relay-notice"
            | "/deletion"
    ) || tail.strip_prefix("/epochs/").is_some_and(|id| {
        canonical_number(
            id.strip_suffix("/recovery-envelope").unwrap_or(id),
            u64::from(u32::MAX),
            false,
        )
    }) || tail
        .strip_prefix("/devices/")
        .and_then(|id| id.strip_suffix("/envelopes"))
        .is_some_and(crate::public_wire::uuid)
        || tail
            .strip_prefix("/receipts/")
            .is_some_and(|id| canonical_number(id, crate::strict::jcs::MAX_INTEGER, true))
        || tail
            .strip_prefix("/snapshots/")
            .is_some_and(|id| crate::public_wire::uuid(id.strip_prefix("parts/").unwrap_or(id)))
}

#[allow(clippy::too_many_arguments)]
pub fn capability(
    handle: u64,
    operation: Operation,
    origin: &str,
    method: &str,
    path: &str,
    query: &str,
    body: &[u8],
) -> Result<String> {
    capability_at(
        handle,
        operation,
        origin,
        method,
        path,
        query,
        body,
        crate::crypto::token_now_ms().map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    )
}

#[allow(clippy::too_many_arguments)]
pub fn capability_at(
    handle: u64,
    operation: Operation,
    origin: &str,
    method: &str,
    path: &str,
    query: &str,
    body: &[u8],
    now: i64,
) -> Result<String> {
    with(handle, operation, |session| {
        let prefix = format!("/pallasync/v3/chains/{}", session.roots.chain_id);
        let tail = path
            .strip_prefix(&prefix)
            .filter(|tail| tail.is_empty() || tail.starts_with('/'));
        let allowed = if method == "GET" {
            tail.is_some_and(catalogue_read)
        } else {
            match operation {
                Operation::Genesis => method == "POST" && path == "/pallasync/v3/chains",
                Operation::Enrollment => method == "POST" && tail == Some("/devices/enroll"),
                Operation::Rotate => method == "POST" && tail == Some("/epochs"),
                Operation::Revoke => {
                    method == "POST"
                        && tail.is_some_and(|t| {
                            t.strip_prefix("/devices/")
                                .and_then(|t| t.strip_suffix("/revoke"))
                                .is_some_and(crate::public_wire::uuid)
                        })
                }
                Operation::Reseed => {
                    (method == "PUT" && tail == Some("/parameters"))
                        || (method == "POST" && tail == Some("/records/repair"))
                        || (method == "POST" && path == "/pallasync/v3/chains/import")
                }
                Operation::Delete => method == "DELETE" && tail == Some(""),
                Operation::Prune => method == "POST" && tail == Some("/log/prune"),
                _ => false,
            }
        };
        if !allowed {
            return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
        }
        crate::crypto::create_capability_token_at(
            origin,
            &session.roots.chain_id,
            "admin",
            None,
            method,
            path,
            query,
            body,
            &session.roots.admin_key,
            120_000,
            now,
        )
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    })
}

pub fn create_set(
    handle: u64,
    operation: Operation,
    generation: u32,
    threshold: u16,
    holders: &[Value],
    now_ms: u64,
) -> Result<Value> {
    if !matches!(
        operation,
        Operation::RecoveryCreate | Operation::RecoveryRotate
    ) {
        return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
    }
    shamir::parameters(
        threshold,
        holders
            .len()
            .try_into()
            .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?,
    )?;
    with(handle, operation, |session| {
        if session.prepared.is_some() {
            return Err(ClientError("C_RECOVERY_ROTATION_INCOMPLETE"));
        }
        let mut independent = BTreeSet::new();
        for h in holders {
            crate::public_wire::fields(
                h,
                &[
                    ("holder_kind", "device|offline"),
                    ("holder_device_id", "uuid?"),
                    ("holder_instance_id", "uuid"),
                ],
                &[],
            )?;
            if (crate::public_wire::s(h, "holder_kind") == "offline")
                != h["holder_device_id"].is_null()
                || !independent.insert(crate::public_wire::s(h, "holder_instance_id"))
            {
                return Err(ClientError("C_RECOVERY_PARAMETER_INVALID"));
            }
        }
        let set_id = uuid::Uuid::new_v4().to_string();
        let mut rms = Locked::new([0; 32])?;
        rand::rngs::OsRng.fill_bytes(&mut *rms);
        let pieces = shamir::split(&rms, threshold, holders.len() as u16)?;
        let input = session
            .input
            .as_ref()
            .ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
        let fingerprint = &input.fingerprint;
        let mut nonce = [0; 24];
        rand::rngs::OsRng.fill_bytes(&mut nonce);
        let mut blob = json!({"type":"PALLASYNC-RECOVERY-BLOB-v3","version":"3.0","chain_id":session.roots.chain_id,"generation":generation,"recovery_set_id":set_id,"threshold":threshold,"total_shares":holders.len(),"recovery_secret_fingerprint":fingerprint,"created_at_ms":now_ms});
        let mut blob_aad = blob.clone();
        blob_aad["type"] = json!("PALLASYNC-RECOVERY-BLOB-AAD-v3");
        let plaintext = SecretJson(
            json!({"mnemonic":input.mnemonic()?,"passphrase":input.passphrase()?,"created_at_ms":now_ms,"recovery_set_id":set_id,"recovery_secret_fingerprint":fingerprint,"chain_id":session.roots.chain_id,"generation":generation}),
        );
        let bytes = Zeroizing::new(recovery::canonical(&plaintext.0)?);
        let frame = Zeroizing::new(
            crate::crypto::pad_and_frame(&bytes, crate::crypto::SIZE_BUCKETS, false)
                .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?,
        );
        let ciphertext = XChaCha20Poly1305::new((&*rms).into())
            .encrypt(
                XNonce::from_slice(&nonce),
                Payload {
                    msg: &frame,
                    aad: &recovery::canonical(&blob_aad)?,
                },
            )
            .map_err(|_| ClientError("C_RECOVERY_BLOB_INVALID"))?;
        blob["blob_nonce"] = json!(URL_SAFE_NO_PAD.encode(nonce));
        blob["encrypted_blob"] = json!(URL_SAFE_NO_PAD.encode(ciphertext));
        let mut bindings = Vec::new();
        for (h, p) in holders.iter().zip(&pieces) {
            let mut b = h.clone();
            b["recovery_share_id"] = json!(uuid::Uuid::new_v4().to_string());
            b["share_index"] = json!(p.index);
            b["share_commitment"] = json!(recovery::commitment(&set_id, p)?);
            bindings.push(b);
        }
        let mut manifest = json!({"type":"PALLASYNC-RECOVERY-SET-v3","version":"3.0","profile":"shamir-gf256-aes/1","chain_id":session.roots.chain_id,"generation":generation,"recovery_set_id":set_id,"threshold":threshold,"total_shares":holders.len(),"recovery_secret_fingerprint":fingerprint,"created_at_ms":now_ms,"admin_public_key":session.roots.admin_public_key,"recovery_blob_hash":object_hash(&blob),"holders":bindings});
        recovery::sign(&mut manifest, &session.roots.admin_key, recovery::CTX_SET)?;
        let mut status = json!({"type":"PALLASYNC-RECOVERY-STATUS-v3","version":"3.0","recovery_set_id":set_id,"set_manifest_hash":object_hash(&manifest),"status_seq":1,"state":"prepared","previous_status_hash":null,"replacement_set_id":null,"created_at_ms":now_ms});
        recovery::sign(&mut status, &session.roots.admin_key, recovery::CTX_STATUS)?;
        recovery::validate_manifest(&manifest, &session.roots.admin_public_key)?;
        let result = json!({"manifest":manifest,"blob":blob,"status_chain":[status]});
        session.prepared = Some(PreparedSet {
            manifest,
            blob,
            statuses: vec![status],
            pieces: pieces
                .iter()
                .map(|piece| Locked::new(piece.clone()))
                .collect::<Result<_>>()?,
            proposals: HashMap::new(),
            acknowledged: BTreeSet::new(),
            threshold_test_passed: false,
        });
        Ok(result)
    })
}

pub(crate) fn distribution_payload(
    handle: u64,
    operation: Operation,
    index: u8,
) -> Result<SecretJson> {
    with(handle, operation, |session| {
        let set = session
            .prepared
            .as_ref()
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        if set.acknowledged.contains(&index) {
            return Err(ClientError("C_RECOVERY_SHARE_ALREADY_USED"));
        }
        let piece = set
            .pieces
            .get(
                usize::from(index)
                    .checked_sub(1)
                    .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?,
            )
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        Ok(SecretJson(
            json!({"manifest":set.manifest,"blob":set.blob,"share_index":index,"raw_share":URL_SAFE_NO_PAD.encode(piece.bytes)}),
        ))
    })
}
pub fn sign_proposal(handle: u64, operation: Operation, proposal: &Value) -> Result<Value> {
    with(handle, operation, |session| {
        recovery::validate_share(proposal, false)?;
        let set = session
            .prepared
            .as_mut()
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        let index = crate::public_wire::n(proposal, "share_index") as u8;
        let holder = crate::public_wire::a(&set.manifest, "holders")
            .get(usize::from(index) - 1)
            .ok_or(ClientError("C_RECOVERY_SHARE_INVALID"))?;
        for key in [
            "chain_id",
            "generation",
            "recovery_set_id",
            "threshold",
            "total_shares",
            "recovery_secret_fingerprint",
            "admin_public_key",
        ] {
            if proposal[key] != set.manifest[key] {
                return Err(ClientError("C_RECOVERY_SET_MISMATCH"));
            }
        }
        for key in [
            "recovery_share_id",
            "share_index",
            "holder_kind",
            "holder_device_id",
            "holder_instance_id",
            "share_commitment",
        ] {
            if proposal[key] != holder[key] {
                return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
            }
        }
        if crate::public_wire::s(proposal, "set_manifest_hash") != object_hash(&set.manifest)
            || crate::public_wire::s(proposal, "recovery_blob_hash") != object_hash(&set.blob)
        {
            return Err(ClientError("C_RECOVERY_SET_MISMATCH"));
        }
        if let Some(previous) = set.proposals.get(&index) {
            let mut unsigned = previous.clone();
            unsigned.as_object_mut().unwrap().remove("signature");
            if unsigned != *proposal {
                return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
            }
            return Ok(previous.clone());
        }
        let mut signed = proposal.clone();
        recovery::sign(&mut signed, &session.roots.admin_key, recovery::CTX_SHARE)?;
        set.proposals.insert(index, signed.clone());
        Ok(signed)
    })
}
pub(crate) fn acknowledge_persisted(handle: u64, operation: Operation, payload: &Value) -> Result {
    with(handle, operation, |session| {
        crate::public_wire::fields(
            payload,
            &[
                ("recovery_share_id", "uuid"),
                ("record_hash", "b32"),
                ("verified", "bool"),
            ],
            &[],
        )?;
        let set = session
            .prepared
            .as_mut()
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        if payload["verified"] != true {
            return Err(ClientError("C_RECOVERY_ROTATION_INCOMPLETE"));
        }
        let (&index, record) = set
            .proposals
            .iter()
            .find(|(_, r)| r["recovery_share_id"] == payload["recovery_share_id"])
            .ok_or(ClientError("C_RECOVERY_SHARE_INVALID"))?;
        if crate::public_wire::s(payload, "record_hash") != object_hash(record) {
            return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
        }
        set.acknowledged.insert(index);
        set.pieces[usize::from(index) - 1].bytes.zeroize();
        Ok(())
    })
}
pub(crate) fn threshold_test(
    handle: u64,
    operation: Operation,
    packages: &[Value],
    pieces: &mut Vec<Locked<Piece>>,
) -> Result {
    with(handle, operation, |session| {
        let set = session
            .prepared
            .as_mut()
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        if packages.len() != pieces.len() || set.acknowledged.len() != set.pieces.len() {
            return Err(ClientError("C_RECOVERY_ROTATION_INCOMPLETE"));
        }
        let mut holders = BTreeSet::new();
        for (package, piece) in packages.iter().zip(pieces.iter()) {
            recovery::validate_package(
                package,
                &session.roots.admin_public_key,
                Some(&object_hash(&set.statuses[0])),
            )?;
            if package["manifest"] != set.manifest
                || package["record"]["share_index"] != json!(piece.index)
                || !holders.insert(crate::public_wire::s(
                    &package["record"],
                    "holder_instance_id",
                ))
                || crate::public_wire::s(&package["record"], "share_commitment")
                    != recovery::commitment(
                        crate::public_wire::s(&set.manifest, "recovery_set_id"),
                        piece,
                    )?
            {
                return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
            }
        }
        let rms = shamir::reconstruct_locked(
            pieces,
            crate::public_wire::n(&set.manifest, "threshold") as u16,
            set.pieces.len() as u16,
        )?;
        pieces.clear();
        let input = recovery::open_blob(&set.blob, &rms, &session.roots.admin_public_key)?;
        if input.fingerprint()?
            != session
                .input
                .as_ref()
                .ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?
                .fingerprint
        {
            return Err(ClientError("C_RECOVERY_ROOT_MISMATCH"));
        }
        set.threshold_test_passed = true;
        Ok(())
    })
}
pub fn activate_set(handle: u64, operation: Operation, now_ms: u64) -> Result<Value> {
    with(handle, operation, |session| {
        let set = session
            .prepared
            .as_mut()
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        if set.acknowledged.len() != set.pieces.len() || !set.threshold_test_passed {
            return Err(ClientError("C_RECOVERY_ROTATION_INCOMPLETE"));
        }
        if set.statuses.last().is_some_and(|s| s["state"] == "active") {
            return Ok(json!(set.statuses));
        }
        if set.statuses.len() != 1 || set.statuses[0]["state"] != "prepared" {
            return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
        }
        let mut status = json!({"type":"PALLASYNC-RECOVERY-STATUS-v3","version":"3.0","recovery_set_id":set.manifest["recovery_set_id"],"set_manifest_hash":object_hash(&set.manifest),"status_seq":2,"state":"active","previous_status_hash":object_hash(&set.statuses[0]),"replacement_set_id":null,"created_at_ms":now_ms});
        recovery::sign(&mut status, &session.roots.admin_key, recovery::CTX_STATUS)?;
        set.statuses.push(status);
        Ok(json!(set.statuses))
    })
}

/// §§7.11.1/18.6–18.8: authoritative retirement/compromise needs scoped root authority.
/// A replacement may name only the new set activated by this rotation's distribution/test guards.
#[allow(clippy::too_many_arguments)]
/// §§18.6–18.8: resume only the public retirement step after an already signed active handoff.
/// This restores neither a dealer session nor its RMS/pieces and cannot activate a prepared set.
pub fn bind_active_replacement(
    handle: u64,
    manifest: &Value,
    statuses: &[Value],
    known_head: &str,
) -> Result<()> {
    use crate::public_wire::s;
    with(handle, Operation::RecoveryRotate, |session| {
        recovery::validate_manifest(manifest, &session.roots.admin_public_key)?;
        recovery::validate_status_chain(statuses, manifest, Some(known_head))?;
        let latest = statuses
            .last()
            .ok_or(ClientError("C_RECOVERY_SHARE_INVALID"))?;
        if object_hash(latest) != known_head || s(latest, "state") != "active" {
            return Err(ClientError("C_RECOVERY_ROTATION_INCOMPLETE"));
        }
        let input = session
            .input
            .as_ref()
            .ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
        if manifest["chain_id"] != session.roots.chain_id
            || s(manifest, "recovery_secret_fingerprint") != input.fingerprint
        {
            return Err(ClientError("C_RECOVERY_ROOT_MISMATCH"));
        }
        let id = s(manifest, "recovery_set_id");
        if session
            .active_replacement
            .as_deref()
            .is_some_and(|previous| previous != id)
        {
            return Err(ClientError("C_RECOVERY_SET_MISMATCH"));
        }
        if let Some(prepared) = &session.prepared
            && (prepared.manifest != *manifest
                || !prepared.threshold_test_passed
                || prepared.acknowledged.len() != prepared.pieces.len()
                || prepared
                    .statuses
                    .last()
                    .is_none_or(|status| object_hash(status) != known_head))
        {
            return Err(ClientError("C_RECOVERY_ROTATION_INCOMPLETE"));
        }
        session.active_replacement = Some(id.to_owned());
        Ok(())
    })
}

#[allow(clippy::too_many_arguments)]
pub fn advance_recovery_status(
    handle: u64,
    operation: Operation,
    manifest: &Value,
    statuses: &[Value],
    known_head: &str,
    state: &str,
    replacement: Option<&str>,
    now_ms: u64,
) -> Result<Value> {
    use crate::public_wire::{n, s};
    if !matches!(
        operation,
        Operation::RecoveryRotate | Operation::RecoveryDisable
    ) {
        return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
    }
    with(handle, operation, |session| {
        recovery::validate_manifest(manifest, &session.roots.admin_public_key)?;
        if manifest["chain_id"] != session.roots.chain_id {
            return Err(ClientError("C_RECOVERY_ROOT_MISMATCH"));
        }
        recovery::validate_status_chain(statuses, manifest, Some(known_head))?;
        let previous = statuses
            .last()
            .ok_or(ClientError("C_RECOVERY_SHARE_INVALID"))?;
        if object_hash(previous) != known_head {
            return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
        }
        if !matches!(state, "retired" | "compromised") || now_ms > crate::strict::jcs::MAX_INTEGER {
            return Err(ClientError("C_RECOVERY_PARAMETER_INVALID"));
        }
        if let Some(replacement) = replacement {
            let complete_live_handoff = session.prepared.as_ref().is_some_and(|active| {
                active.manifest["recovery_set_id"] == replacement
                    && active
                        .statuses
                        .last()
                        .is_some_and(|status| status["state"] == "active")
                    && active.acknowledged.len() == active.pieces.len()
                    && active.threshold_test_passed
            });
            if operation != Operation::RecoveryRotate
                || manifest["recovery_set_id"] == replacement
                || !(complete_live_handoff
                    || session.active_replacement.as_deref() == Some(replacement))
            {
                return Err(ClientError("C_RECOVERY_ROTATION_INCOMPLETE"));
            }
        }
        let sequence = n(previous, "status_seq")
            .checked_add(1)
            .filter(|seq| *seq <= crate::strict::jcs::MAX_INTEGER)
            .ok_or(ClientError("C_RECOVERY_SHARE_INVALID"))?;
        let mut status = json!({"type":"PALLASYNC-RECOVERY-STATUS-v3","version":"3.0",
            "recovery_set_id":manifest["recovery_set_id"],"set_manifest_hash":object_hash(manifest),
            "status_seq":sequence,"state":state,"previous_status_hash":known_head,
            "replacement_set_id":replacement,"created_at_ms":now_ms});
        // Check transitions before signing, including the terminal compromised state.
        if !matches!(
            (s(previous, "state"), state),
            ("prepared", "retired" | "compromised")
                | ("active", "retired" | "compromised")
                | ("retired", "compromised")
        ) {
            return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
        }
        recovery::sign(&mut status, &session.roots.admin_key, recovery::CTX_STATUS)?;
        let mut updated = statuses.to_vec();
        updated.push(status);
        recovery::validate_status_chain(&updated, manifest, Some(known_head))?;
        if let Some(prepared) = session.prepared.as_mut()
            && prepared.manifest["recovery_set_id"] == manifest["recovery_set_id"]
        {
            prepared.statuses = updated.clone();
        }
        Ok(json!(updated))
    })
}
