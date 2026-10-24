//! Native role processing: raw pieces only move between protected RAM and HPKE.
use crate::{
    client_error::{ClientError, Result},
    local_transfer::{self, Reply},
    locked_memory::Locked,
    public_wire::{n, s},
    recovery::{self, SecretJson},
    root_session::{self, Operation},
    shamir::Piece,
    strict::verify::object_hash,
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use rand::RngCore;
use serde_json::{Value, json};
use std::{
    collections::HashMap,
    sync::{Mutex, OnceLock},
    time::{Duration, Instant},
};
use zeroize::Zeroizing;

struct Holder {
    manifest: Value,
    blob: Value,
    proposal: Value,
    record: Option<Value>,
    rswk: Locked<[u8; 32]>,
    offline: Option<Value>,
}
static HOLDERS: OnceLock<Mutex<HashMap<u64, Holder>>> = OnceLock::new();
fn holders() -> &'static Mutex<HashMap<u64, Holder>> {
    HOLDERS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn invalid() -> ClientError {
    ClientError("C_RECOVERY_SHARE_INVALID")
}
pub(crate) fn discard_holder(pair: u64) {
    if let Ok(mut map) = holders().lock() {
        map.remove(&pair);
    }
}

pub fn distribute(root: u64, operation: Operation, pair: u64, index: u8) -> Result<Value> {
    let payload = root_session::distribution_payload(root, operation, index)?;
    local_transfer::encrypt(pair, "distribute_share", &payload.0)
}
pub fn receive_dealer(root: u64, operation: Operation, pair: u64, bytes: &[u8]) -> Result<Value> {
    local_transfer::receive(pair, bytes, |kind, payload| match kind {
        "wrap_proposal" => {
            let record = root_session::sign_proposal(root, operation, &payload["record"])?;
            Ok(Reply::Transfer("signed_share", json!({"record":record})))
        }
        "persisted" => {
            root_session::acknowledge_persisted(root, operation, payload)?;
            Ok(Reply::Public(payload.clone()))
        }
        _ => Err(invalid()),
    })
}
/// Storage readiness and occupied-holder replacement are confirmed before accepting a piece.
#[allow(clippy::too_many_arguments)]
pub fn receive_holder(
    pair: u64,
    bytes: &[u8],
    instance: &str,
    device: Option<&str>,
    protection: &str,
    storage_ready: bool,
    replacement_confirmed: bool,
    occupied: bool,
    _now_ms: u64,
) -> Result<Value> {
    if !storage_ready {
        return Err(ClientError("C_RECOVERY_STORAGE_UNSAFE"));
    }
    if occupied && !replacement_confirmed {
        return Err(ClientError("C_RECOVERY_SHARE_ALREADY_USED"));
    }
    local_transfer::receive(pair, bytes, |kind, payload| {
        let mut map = holders().lock().map_err(|_| invalid())?;
        match kind {
            "distribute_share" => {
                if map.contains_key(&pair) {
                    return Err(ClientError("C_RECOVERY_SHARE_ALREADY_USED"));
                }
                let m = &payload["manifest"];
                let b = &payload["blob"];
                let index = n(payload, "share_index") as u8;
                let h = &m["holders"][usize::from(index) - 1];
                if s(h, "holder_instance_id") != instance || h["holder_device_id"] != json!(device)
                {
                    return Err(ClientError("C_RECOVERY_SET_MISMATCH"));
                }
                let piece = recovery::decode_piece(index, s(payload, "raw_share"))?;
                let mut rswk = Locked::new([0; 32])?;
                rand::rngs::OsRng.fill_bytes(&mut *rswk);
                let proposal = recovery::wrap_proposal(
                    m,
                    b,
                    &piece,
                    &rswk,
                    &uuid::Uuid::new_v4().to_string(),
                    protection,
                )?;
                map.insert(
                    pair,
                    Holder {
                        manifest: m.clone(),
                        blob: b.clone(),
                        proposal: proposal.clone(),
                        record: None,
                        rswk,
                        offline: None,
                    },
                );
                Ok(Reply::Transfer("wrap_proposal", json!({"record":proposal})))
            }
            "signed_share" => {
                let h = map.get_mut(&pair).ok_or_else(invalid)?;
                let record = &payload["record"];
                let mut unsigned = record.clone();
                unsigned
                    .as_object_mut()
                    .ok_or_else(invalid)?
                    .remove("signature");
                if unsigned != h.proposal {
                    return Err(invalid());
                }
                recovery::unwrap_piece(record, &h.rswk)?;
                h.record = Some(record.clone());
                Ok(Reply::Public(json!({"ready_to_persist":true})))
            }
            _ => Err(invalid()),
        }
    })
}
pub fn holder_package(pair: u64, statuses: &[Value], credential: Option<&[u8]>) -> Result<Value> {
    local_transfer::context(pair)?;
    let mut map = holders().lock().map_err(|_| invalid())?;
    let h = map.get_mut(&pair).ok_or_else(invalid)?;
    let record = h.record.as_ref().ok_or_else(invalid)?;
    if record["holder_kind"] == "offline" && h.offline.is_none() {
        h.offline = Some(recovery::wrap_offline_key(
            record,
            &h.rswk,
            credential.ok_or(ClientError("C_RECOVERY_WRAP_KEY_UNAVAILABLE"))?,
        )?);
    }
    let package = json!({"type":"PALLASYNC-RECOVERY-PACKAGE-v3","version":"3.0","manifest":h.manifest,"status_chain":statuses,"record":record,"blob":h.blob,"offline_key_envelope":h.offline});
    recovery::validate_package(&package, s(&h.manifest, "admin_public_key"), None)?;
    drop(map);
    local_transfer::context(pair)?;
    Ok(package)
}
/// Returned only as JNI bytes to the OS wrapping operation; never as JSON/text or a file.
pub fn holder_wrap_key(pair: u64) -> Result<Zeroizing<[u8; 32]>> {
    local_transfer::context(pair)?;
    let map = holders().lock().map_err(|_| invalid())?;
    let h = map.get(&pair).ok_or_else(invalid)?;
    let key = Zeroizing::new(*h.rswk);
    drop(map);
    local_transfer::context(pair)?;
    Ok(key)
}
pub fn persisted(pair: u64, package: &Value, readback_key: &[u8; 32]) -> Result<Value> {
    recovery::validate_package(package, s(&package["manifest"], "admin_public_key"), None)?;
    let payload = {
        let map = holders().lock().map_err(|_| invalid())?;
        let h = map.get(&pair).ok_or_else(invalid)?;
        if package["manifest"] != h.manifest || Some(&package["record"]) != h.record.as_ref() {
            return Err(invalid());
        }
        recovery::unwrap_piece(&package["record"], readback_key)?;
        json!({"recovery_share_id":package["record"]["recovery_share_id"],"record_hash":object_hash(&package["record"]),"verified":true})
    };
    let packet = local_transfer::encrypt(pair, "persisted", &payload)?;
    holders().lock().map_err(|_| invalid())?.remove(&pair);
    Ok(packet)
}
pub fn release_piece(
    pair: u64,
    package: &Value,
    rswk: &[u8; 32],
    authenticated: bool,
    consented: bool,
) -> Result<Value> {
    release_piece_guarded(pair, package, rswk, authenticated, consented, false, false)
}
#[allow(clippy::too_many_arguments)]
pub fn release_piece_guarded(
    pair: u64,
    package: &Value,
    rswk: &[u8; 32],
    authenticated: bool,
    consented: bool,
    locally_compromised: bool,
    emergency_new_root: bool,
) -> Result<Value> {
    if !authenticated || !consented {
        return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
    }
    recovery::validate_package(package, s(&package["manifest"], "admin_public_key"), None)?;
    let context = local_transfer::context(pair)?;
    let status = package["status_chain"]
        .as_array()
        .and_then(|a| a.last())
        .ok_or_else(invalid)?;
    usable_state(
        s(status, "state"),
        s(&context, "purpose"),
        locally_compromised,
        emergency_new_root,
    )?;
    let piece = recovery::unwrap_piece(&package["record"], rswk)?;
    let payload =
        SecretJson(json!({"package":package,"raw_share":URL_SAFE_NO_PAD.encode(piece.bytes)}));
    local_transfer::encrypt(pair, "collect_share", &payload.0)
}
pub fn end(pair: u64) {
    if let Ok(mut map) = holders().lock() {
        map.remove(&pair);
    }
    local_transfer::end(pair);
}
pub fn end_all() {
    if let Ok(mut map) = holders().lock() {
        map.clear();
    }
    if let Ok(mut map) = collectors().lock() {
        map.clear();
    }
    local_transfer::end_all();
}

/// §18.7: a newly suspected leak aborts matching in-flight ceremonies and unconsumed recovered roots.
/// This does not fabricate an admin-signed compromised status or erase valid stored packages.
pub fn cancel_set(set_id: &str) -> Result {
    crate::public_wire::fields(
        &json!({"recovery_set_id":set_id}),
        &[("recovery_set_id", "uuid")],
        &[],
    )
    .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
    root_session::invalidate_recovery_set(set_id);
    if let Ok(mut map) = collectors().lock() {
        map.retain(|_, collector| collector.manifest["recovery_set_id"] != set_id);
    }
    local_transfer::invalidate_recovery_set(set_id);
    Ok(())
}

fn usable_state(state: &str, purpose: &str, compromised: bool, emergency: bool) -> Result {
    if state == "retired" {
        return Err(ClientError("C_RECOVERY_SET_RETIRED"));
    }
    if compromised || state == "compromised" {
        return if emergency && purpose == "recover" {
            Ok(())
        } else {
            Err(ClientError("C_RECOVERY_SHARE_COMPROMISED"))
        };
    }
    if state == "active" || (state == "prepared" && matches!(purpose, "create" | "rotate")) {
        Ok(())
    } else {
        Err(ClientError("C_RECOVERY_ROTATION_INCOMPLETE"))
    }
}

#[derive(Clone, Copy)]
pub enum CollectionMode {
    SetupTest {
        root: u64,
        operation: Operation,
    },
    Recover {
        operation: Operation,
        emergency_new_root: bool,
    },
}
struct Collector {
    lease: crate::secret_lifetime::Lease,
    manifest: Value,
    statuses: Vec<Value>,
    mode: CollectionMode,
    ceremony_id: String,
    pieces: Vec<Locked<Piece>>,
    packages: Vec<Value>,
    own_received: bool,
    last_interaction: Instant,
}
static COLLECTORS: OnceLock<Mutex<HashMap<u64, Collector>>> = OnceLock::new();
fn collectors() -> &'static Mutex<HashMap<u64, Collector>> {
    COLLECTORS.get_or_init(|| Mutex::new(HashMap::new()))
}
pub(crate) fn expire_collectors() {
    if let Ok(mut map) = collectors().lock() {
        map.retain(|_, c| {
            c.lease.live() && c.last_interaction.elapsed() < Duration::from_secs(300)
        });
    }
}
pub fn end_collection(handle: u64) {
    if let Ok(mut map) = collectors().lock() {
        map.remove(&handle);
    }
}
pub fn touch_collection(handle: u64) -> Result {
    let mut map = collectors().lock().map_err(|_| invalid())?;
    let c = map
        .get_mut(&handle)
        .ok_or(ClientError("C_RECOVERY_SESSION_EXPIRED"))?;
    if !c.lease.live() || c.last_interaction.elapsed() >= Duration::from_secs(300) {
        map.remove(&handle);
        return Err(ClientError("C_RECOVERY_SESSION_EXPIRED"));
    }
    c.last_interaction = Instant::now();
    Ok(())
}
#[allow(clippy::too_many_arguments)]
pub fn begin_collection(
    manifest: &Value,
    statuses: &[Value],
    admin: &str,
    known_status: Option<&str>,
    ceremony_id: &str,
    mode: CollectionMode,
    explicitly_confirmed: bool,
    locally_compromised: bool,
) -> Result<u64> {
    let lease = crate::secret_lifetime::lease()?;
    if !explicitly_confirmed {
        return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
    }
    crate::secret_lifetime::start();
    crate::public_wire::fields(
        &json!({"ceremony_id":ceremony_id}),
        &[("ceremony_id", "uuid")],
        &[],
    )?;
    recovery::validate_manifest(manifest, admin)?;
    recovery::validate_status_chain(statuses, manifest, known_status)?;
    let state = s(statuses.last().ok_or_else(invalid)?, "state");
    match mode {
        CollectionMode::SetupTest { root, operation } => {
            if !matches!(
                operation,
                Operation::RecoveryCreate | Operation::RecoveryRotate
            ) {
                return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
            }
            usable_state(
                state,
                if operation == Operation::RecoveryCreate {
                    "create"
                } else {
                    "rotate"
                },
                locally_compromised,
                false,
            )?;
            root_session::with(root, operation, |session| {
                let set = session.prepared.as_ref().ok_or_else(invalid)?;
                if set.manifest != *manifest || set.statuses != statuses {
                    return Err(invalid());
                }
                Ok(())
            })?;
        }
        CollectionMode::Recover {
            operation,
            emergency_new_root,
        } => {
            if emergency_new_root && operation != Operation::NewRootMigration {
                return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
            }
            usable_state(state, "recover", locally_compromised, emergency_new_root)?;
        }
    }
    let mut map = collectors().lock().map_err(|_| invalid())?;
    lease.check()?;
    let mut handle = rand::random::<u64>() & i64::MAX as u64;
    while handle == 0 || map.contains_key(&handle) {
        handle = rand::random::<u64>() & i64::MAX as u64;
    }
    map.insert(
        handle,
        Collector {
            lease,
            manifest: manifest.clone(),
            statuses: statuses.to_vec(),
            mode,
            ceremony_id: ceremony_id.into(),
            pieces: Vec::new(),
            packages: Vec::new(),
            own_received: false,
            last_interaction: Instant::now(),
        },
    );
    Ok(handle)
}
fn collect(c: &mut Collector, package: &Value, piece: Piece) -> Result<bool> {
    c.lease.check()?;
    recovery::validate_package(
        package,
        s(&c.manifest, "admin_public_key"),
        Some(&object_hash(c.statuses.last().ok_or_else(invalid)?)),
    )?;
    if package["manifest"] != c.manifest
        || package["status_chain"] != json!(c.statuses)
        || n(&package["record"], "share_index") != u64::from(piece.index)
        || s(&package["record"], "share_commitment")
            != recovery::commitment(s(&c.manifest, "recovery_set_id"), &piece)?
    {
        return Err(ClientError("C_RECOVERY_SET_MISMATCH"));
    }
    if let Some(i) = c.pieces.iter().position(|p| p.index == piece.index) {
        if c.packages[i]["record"] != package["record"] || c.pieces[i].bytes != piece.bytes {
            return Err(invalid());
        }
        return Ok(false);
    }
    if c.packages
        .iter()
        .any(|p| p["record"]["holder_instance_id"] == package["record"]["holder_instance_id"])
    {
        return Err(ClientError("C_RECOVERY_SHARE_ALREADY_USED"));
    }
    c.pieces.push(Locked::new(piece)?);
    c.packages.push(package.clone());
    Ok(true)
}
pub fn receive_collector(collector: u64, pair: u64, bytes: &[u8]) -> Result<Value> {
    let context = local_transfer::context(pair)?;
    let outcome = local_transfer::receive(pair, bytes, |kind, payload| {
        if kind != "collect_share" {
            return Err(invalid());
        }
        let mut map = collectors().lock().map_err(|_| invalid())?;
        let c = map
            .get_mut(&collector)
            .ok_or(ClientError("C_RECOVERY_SESSION_EXPIRED"))?;
        if !c.lease.live() || c.last_interaction.elapsed() >= Duration::from_secs(300) {
            return Err(ClientError("C_RECOVERY_SESSION_EXPIRED"));
        }
        let purpose = match c.mode {
            CollectionMode::SetupTest {
                operation: Operation::RecoveryCreate,
                ..
            } => "create",
            CollectionMode::SetupTest { .. } => "rotate",
            CollectionMode::Recover {
                operation: Operation::RecoveryDisable,
                ..
            } => "disable",
            CollectionMode::Recover { .. } => "recover",
        };
        if s(&context, "role") != "collector"
            || s(&context, "purpose") != purpose
            || s(&context, "ceremony_id") != c.ceremony_id
            || s(&context, "set_manifest_hash") != object_hash(&c.manifest)
        {
            return Err(ClientError("C_RECOVERY_SET_MISMATCH"));
        }
        let package = &payload["package"];
        let piece = recovery::decode_piece(
            n(&package["record"], "share_index") as u8,
            s(payload, "raw_share"),
        )?;
        let added = collect(c, package, piece)?;
        Ok(Reply::Public(
            json!({"accepted":added,"distinct_shares":c.pieces.len(),"threshold":c.manifest["threshold"]}),
        ))
    });
    match outcome {
        Ok(v) => Ok(v),
        Err(e) => {
            if e.0 != "C_MISSING_DEPENDENCY" {
                end_collection(collector);
            }
            Err(e)
        }
    }
}
pub fn collect_own_piece(
    collector: u64,
    package: &Value,
    rswk: &[u8; 32],
    instance: &str,
    authenticated: bool,
    consented: bool,
) -> Result<Value> {
    collect_own_piece_guarded(
        collector,
        package,
        rswk,
        instance,
        authenticated,
        consented,
        false,
    )
}
#[allow(clippy::too_many_arguments)]
pub fn collect_own_piece_guarded(
    collector: u64,
    package: &Value,
    rswk: &[u8; 32],
    instance: &str,
    authenticated: bool,
    consented: bool,
    locally_compromised: bool,
) -> Result<Value> {
    if !authenticated || !consented {
        return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
    }
    let result = (|| {
        let mut map = collectors().lock().map_err(|_| invalid())?;
        let c = map
            .get_mut(&collector)
            .ok_or(ClientError("C_RECOVERY_SESSION_EXPIRED"))?;
        if locally_compromised
            && !matches!(
                c.mode,
                CollectionMode::Recover {
                    operation: Operation::NewRootMigration,
                    emergency_new_root: true
                }
            )
        {
            return Err(ClientError("C_RECOVERY_SHARE_COMPROMISED"));
        }
        if !c.lease.live() || c.last_interaction.elapsed() >= Duration::from_secs(300) {
            return Err(ClientError("C_RECOVERY_SESSION_EXPIRED"));
        }
        if c.own_received
            || s(&package["record"], "holder_kind") != "device"
            || s(&package["record"], "holder_instance_id") != instance
        {
            return Err(ClientError("C_RECOVERY_SHARE_ALREADY_USED"));
        }
        let piece = recovery::unwrap_piece(&package["record"], rswk)?;
        let added = collect(c, package, piece)?;
        c.own_received = true;
        Ok(
            json!({"accepted":added,"distinct_shares":c.pieces.len(),"threshold":c.manifest["threshold"]}),
        )
    })();
    if result.is_err() {
        end_collection(collector);
    }
    result
}
/// A setup test grants no authority; recovery returns only a named-operation handle.
pub fn finish_collection(collector: u64, explicitly_confirmed: bool) -> Result<Value> {
    let mut c = collectors()
        .lock()
        .map_err(|_| invalid())?
        .remove(&collector)
        .ok_or(ClientError("C_RECOVERY_SESSION_EXPIRED"))?;
    if !explicitly_confirmed {
        return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
    }
    if !c.lease.live() || c.last_interaction.elapsed() >= Duration::from_secs(300) {
        return Err(ClientError("C_RECOVERY_SESSION_EXPIRED"));
    }
    match c.mode {
        CollectionMode::SetupTest { root, operation } => {
            root_session::threshold_test(root, operation, &c.packages, &mut c.pieces)?;
            Ok(json!({"threshold_test_passed":true}))
        }
        CollectionMode::Recover { operation, .. } => {
            let rms = crate::shamir::reconstruct_locked(
                &c.pieces,
                n(&c.manifest, "threshold") as u16,
                n(&c.manifest, "total_shares") as u16,
            )?;
            c.pieces.clear();
            let input = recovery::open_blob(
                &c.packages
                    .first()
                    .ok_or(ClientError("C_RECOVERY_THRESHOLD_NOT_MET"))?["blob"],
                &rms,
                s(&c.manifest, "admin_public_key"),
            )?;
            let root = root_session::insert_input_at(
                input,
                operation,
                Some(s(&c.manifest, "chain_id")),
                Some(s(&c.manifest, "admin_public_key")),
                c.lease,
                Some(s(&c.manifest, "recovery_set_id")),
            )?;
            match root_session::identity(root) {
                Ok(identity) => Ok(json!({"root_session":root,"identity":identity})),
                Err(error) => {
                    root_session::end(root);
                    Err(error)
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn lazy_pair_expiry_also_drops_the_holders_locked_rswk() {
        let package =
            recovery::parse(include_bytes!("../tests/fixtures/recovery-package-c8.json")).unwrap();
        let manifest = &package["manifest"];
        let admin = s(manifest, "admin_public_key");
        let ceremony = uuid::Uuid::new_v4().to_string();
        let session = uuid::Uuid::new_v4().to_string();
        let (dealer, dd) = local_transfer::begin(
            manifest,
            admin,
            &ceremony,
            &session,
            "initiator",
            "dealer",
            "create",
        )
        .unwrap();
        let (holder, hd) = local_transfer::begin(
            manifest,
            admin,
            &ceremony,
            &session,
            "responder",
            "holder",
            "create",
        )
        .unwrap();
        let sas = local_transfer::bind_peer(dealer, &hd).unwrap();
        assert_eq!(sas, local_transfer::bind_peer(holder, &dd).unwrap());
        local_transfer::confirm(dealer, &sas, true).unwrap();
        local_transfer::confirm(holder, &sas, true).unwrap();
        let packet = local_transfer::encrypt(dealer,"distribute_share",&json!({"manifest":manifest,"blob":package["blob"],"share_index":1,"raw_share":URL_SAFE_NO_PAD.encode([0x20u8;32])})).unwrap();
        let binding = &manifest["holders"][0];
        receive_holder(
            holder,
            &recovery::canonical(&packet).unwrap(),
            s(binding, "holder_instance_id"),
            Some(s(binding, "holder_device_id")),
            "os-keystore",
            true,
            false,
            false,
            1,
        )
        .unwrap();
        assert!(holders().lock().unwrap().contains_key(&holder));
        local_transfer::force_pair_expiry_for_test(holder);
        assert_eq!(
            local_transfer::context(holder).unwrap_err().0,
            "C_RECOVERY_SESSION_EXPIRED"
        );
        assert!(!holders().lock().unwrap().contains_key(&holder));
        assert!(local_transfer::context(dealer).is_ok());
        end(dealer);
    }
}
