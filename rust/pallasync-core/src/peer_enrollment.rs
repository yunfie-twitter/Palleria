//! §§10.7/18.1. Physically selected public offers, request-bound SAS and two local confirmations.
//! Private device keys remain in a foreground, expiring native session; no root authority is used.
use crate::{
    authority::Authority,
    client_error::{ClientError, Result},
    crypto,
    locked_memory::Locked,
    public_wire::{self as wire, fields, n, s},
    secret_lifetime::{self, Lease},
    strict::{jcs, verify::*},
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::SigningKey;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::{
    collections::{BTreeMap, HashMap},
    sync::{
        Mutex, OnceLock,
        atomic::{AtomicU64, Ordering},
    },
    time::{Duration, Instant},
};
use subtle::ConstantTimeEq;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

fn require(ok: bool, code: &'static str) -> Result {
    if ok { Ok(()) } else { Err(ClientError(code)) }
}

/// The exact §18.1 public QR shape; this is neither a capability nor a recovery artifact.
pub fn offer(
    authority: &Authority,
    device: &str,
    request: &str,
    nonce: &[u8],
    expires: u64,
    now: u64,
) -> Result<Value> {
    authority.current_key_recipient(device)?;
    let parameters = authority
        .parameters
        .as_ref()
        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
    require(
        parameters["policy"]["allow_peer_enrollment"] == true,
        "C_BOOTSTRAP_TRUST_REQUIRED",
    )?;
    let record = &authority
        .devices
        .get(device)
        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?
        .record;
    let value = json!({"protocol":"pallasync/3","relay_origin":authority.pin.origin,"chain_id":authority.pin.chain_id,
        "generation":authority.generation()?,"parameters_hash":object_hash(parameters),"epoch_hash":object_hash(&authority.current_epoch()?.record),
        "request_id":request,"offer_nonce":URL_SAFE_NO_PAD.encode(nonce),"expires_at_ms":expires,"admin_public_key":authority.admin_public_key,
        "approver_device_id":device,"approver_device_public_key":record["device_public_key"],
        "approver_device_kex_public_key":record["device_kex_public_key"],"approver_device_record_hash":object_hash(record)});
    validate_offer(&value, authority, now)?;
    Ok(value)
}

pub fn validate_offer(value: &Value, authority: &Authority, now: u64) -> Result {
    fields(
        value,
        &[
            ("protocol", "pallasync/3"),
            ("relay_origin", "string"),
            ("chain_id", "b32"),
            ("generation", "u32"),
            ("parameters_hash", "b32"),
            ("epoch_hash", "b32"),
            ("request_id", "uuid"),
            ("offer_nonce", "b32"),
            ("expires_at_ms", "int"),
            ("admin_public_key", "b32"),
            ("approver_device_id", "uuid"),
            ("approver_device_public_key", "b32"),
            ("approver_device_kex_public_key", "b32"),
            ("approver_device_record_hash", "b32"),
        ],
        &[],
    )?;
    require(
        s(value, "relay_origin").starts_with("https://")
            && crypto::normalize_origin(s(value, "relay_origin"))
                .is_ok_and(|origin| origin == s(value, "relay_origin")),
        "C_SCHEMA_VIOLATION",
    )?;
    require(
        now <= jcs::MAX_INTEGER
            && n(value, "expires_at_ms") > now
            && n(value, "expires_at_ms") - now <= 300_000,
        "C_BOOTSTRAP_TRUST_REQUIRED",
    )?;
    authority.current_key_recipient(s(value, "approver_device_id"))?;
    let parameters = authority
        .parameters
        .as_ref()
        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
    let record = &authority
        .devices
        .get(s(value, "approver_device_id"))
        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?
        .record;
    require(
        parameters["policy"]["allow_peer_enrollment"] == true
            && value["relay_origin"] == authority.pin.origin
            && value["chain_id"] == authority.pin.chain_id
            && n(value, "generation") == u64::from(authority.generation()?)
            && value["admin_public_key"] == authority.admin_public_key
            && s(value, "parameters_hash") == object_hash(parameters)
            && s(value, "epoch_hash") == object_hash(&authority.current_epoch()?.record)
            && value["approver_device_public_key"] == record["device_public_key"]
            && value["approver_device_kex_public_key"] == record["device_kex_public_key"]
            && s(value, "approver_device_record_hash") == object_hash(record),
        "C_BOOTSTRAP_TRUST_REQUIRED",
    )?;
    require(
        valid_ed_key(s(value, "admin_public_key"))
            && valid_ed_key(s(value, "approver_device_public_key"))
            && valid_x_key(s(value, "approver_device_kex_public_key")),
        "C_SCHEMA_VIOLATION",
    )
}

fn request_matches(offer: &Value, request: &Value, now: u64) -> Result {
    wire::validate(request, "request")?;
    require(
        ["chain_id", "generation", "request_id", "offer_nonce"]
            .iter()
            .all(|key| request[*key] == offer[*key])
            && n(request, "expires_at_ms") <= n(offer, "expires_at_ms")
            && n(request, "expires_at_ms") > now
            && n(request, "created_at_ms") <= now.saturating_add(60_000),
        "C_BOOTSTRAP_TRUST_REQUIRED",
    )?;
    require(
        verify_signed_json(
            s(request, "device_public_key"),
            s(request, "signature"),
            request,
            CTX_REQUEST,
        )
        .unwrap_or(false),
        "C_SIGNATURE_INVALID",
    )
}

fn sas(offer: &Value, request: &Value, private: &[u8; 32], peer: &str) -> Result<String> {
    require(valid_x_key(peer), "C_SCHEMA_VIOLATION")?;
    let peer: [u8; 32] = decode(peer, Some(32))
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?
        .try_into()
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let shared = Zeroizing::new(
        x25519_dalek::StaticSecret::from(*private)
            .diffie_hellman(&x25519_dalek::PublicKey::from(peer))
            .to_bytes(),
    );
    require(
        !bool::from(shared.as_slice().ct_eq(&[0; 32])),
        "C_SCHEMA_VIOLATION",
    )?;
    let transcript =
        jcs::canonicalize_json(&json!({"offer":offer,"request_hash":object_hash(request)}))
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let mut digest = Sha256::new();
    digest.update(b"PALLASYNC-ENROLL-SAS-v3\0");
    digest.update(shared.as_slice());
    digest.update(transcript);
    Ok(digest.finalize()[..6]
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect())
}

#[derive(Zeroize, ZeroizeOnDrop)]
struct Keys {
    signing: [u8; 32],
    kex: [u8; 32],
}
#[derive(Clone, Copy, PartialEq)]
enum Role {
    Approver,
    Subject,
}
struct Session {
    role: Role,
    keys: Locked<Keys>,
    offer: Value,
    request: Option<Value>,
    sas: Option<String>,
    confirmed: bool,
    certificate: Option<Value>,
    envelopes: BTreeMap<u32, Value>,
    prepared_record: Option<(Value, String, String, String)>,
    started: Instant,
    lease: Lease,
}
static ALL: OnceLock<Mutex<HashMap<u64, Session>>> = OnceLock::new();
static NEXT: AtomicU64 = AtomicU64::new(1);
fn sessions() -> &'static Mutex<HashMap<u64, Session>> {
    ALL.get_or_init(|| Mutex::new(HashMap::new()))
}
fn live(session: &Session, now: u64) -> bool {
    session.lease.live()
        && session.started.elapsed() < Duration::from_secs(300)
        && now < n(&session.offer, "expires_at_ms")
}
pub(crate) fn expire() {
    if let Some(all) = ALL.get()
        && let Ok(mut all) = all.lock()
    {
        all.retain(|_, session| {
            session.lease.live() && session.started.elapsed() < Duration::from_secs(300)
        });
    }
}
fn insert(session: Session) -> Result<u64> {
    secret_lifetime::start();
    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.retain(|_, session| {
        session.lease.live() && session.started.elapsed() < Duration::from_secs(300)
    });
    require(all.len() < 16, "C_LOCAL_LIMIT")?;
    let id = NEXT
        .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |id| {
            id.checked_add(1).filter(|id| *id <= i64::MAX as u64)
        })
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    all.insert(id, session);
    Ok(id)
}
fn with<T>(id: u64, now: u64, action: impl FnOnce(&mut Session) -> Result<T>) -> Result<T> {
    let mut all = sessions()
        .lock()
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
    let result = match all.get_mut(&id) {
        Some(session) if live(session, now) => {
            let result = action(session);
            if result.is_ok() && !live(session, now) {
                Err(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))
            } else {
                result
            }
        }
        _ => Err(ClientError("C_BOOTSTRAP_TRUST_REQUIRED")),
    };
    if result.is_err() {
        all.remove(&id);
    }
    result
}
pub fn end(id: u64) {
    if let Ok(mut all) = sessions().lock() {
        all.remove(&id);
    }
}

pub fn begin_subject(
    offer: &Value,
    authority: &Authority,
    explicitly_selected: bool,
    now: u64,
) -> Result<(u64, Value)> {
    require(explicitly_selected, "C_BOOTSTRAP_TRUST_REQUIRED")?;
    validate_offer(offer, authority, now)?;
    let lease = secret_lifetime::lease()?;
    for _ in 0..16 {
        let (signing, kex) = crypto::generate_device_keys();
        let kex = Zeroizing::new(kex);
        let public = x25519_dalek::PublicKey::from(&x25519_dalek::StaticSecret::from(*kex));
        let mut request = json!({"request_id":offer["request_id"],"chain_id":offer["chain_id"],"generation":offer["generation"],
            "device_id":uuid::Uuid::new_v4().to_string(),"device_public_key":URL_SAFE_NO_PAD.encode(signing.verifying_key().as_bytes()),
            "device_kex_public_key":URL_SAFE_NO_PAD.encode(public.as_bytes()),"offer_nonce":offer["offer_nonce"],"created_at_ms":now,"expires_at_ms":offer["expires_at_ms"]});
        if crate::recovery::sign(&mut request, &signing, CTX_REQUEST).is_err() {
            continue;
        }
        request_matches(offer, &request, now)?;
        require(
            !authority.devices.contains_key(s(&request, "device_id")),
            "C_DEVICE_EQUIVOCATION",
        )?;
        let visual = sas(
            offer,
            &request,
            &kex,
            s(offer, "approver_device_kex_public_key"),
        )?;
        let session = Session {
            role: Role::Subject,
            keys: Locked::new(Keys {
                signing: signing.to_bytes(),
                kex: *kex,
            })?,
            offer: offer.clone(),
            request: Some(request.clone()),
            sas: Some(visual),
            confirmed: false,
            certificate: None,
            envelopes: BTreeMap::new(),
            prepared_record: None,
            started: Instant::now(),
            lease,
        };
        return Ok((insert(session)?, request));
    }
    Err(ClientError("C_SIGNATURE_INVALID"))
}

pub fn begin_approver(
    offer: &Value,
    authority: &Authority,
    signing: &[u8],
    kex: &[u8],
    now: u64,
) -> Result<u64> {
    validate_offer(offer, authority, now)?;
    let keys = Keys {
        signing: signing
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        kex: kex
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    };
    let keys = Locked::new(keys)?;
    let public = SigningKey::from_bytes(&keys.signing).verifying_key();
    let kex_public = x25519_dalek::PublicKey::from(&x25519_dalek::StaticSecret::from(keys.kex));
    require(
        s(offer, "approver_device_public_key") == URL_SAFE_NO_PAD.encode(public.as_bytes())
            && s(offer, "approver_device_kex_public_key")
                == URL_SAFE_NO_PAD.encode(kex_public.as_bytes()),
        "C_BOOTSTRAP_TRUST_REQUIRED",
    )?;
    insert(Session {
        role: Role::Approver,
        keys,
        offer: offer.clone(),
        request: None,
        sas: None,
        confirmed: false,
        certificate: None,
        envelopes: BTreeMap::new(),
        prepared_record: None,
        started: Instant::now(),
        lease: secret_lifetime::lease()?,
    })
}

pub fn bind_request(id: u64, request: &Value, now: u64) -> Result<String> {
    with(id, now, |session| {
        require(session.role == Role::Approver, "C_BOOTSTRAP_TRUST_REQUIRED")?;
        request_matches(&session.offer, request, now)?;
        if let Some(original) = &session.request {
            require(original == request, "C_BOOTSTRAP_TRUST_REQUIRED")?;
        } else {
            let visual = sas(
                &session.offer,
                request,
                &session.keys.kex,
                s(request, "device_kex_public_key"),
            )?;
            session.request = Some(request.clone());
            session.sas = Some(visual);
        }
        session
            .sas
            .clone()
            .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))
    })
}
pub fn visual_sas(id: u64, now: u64) -> Result<String> {
    with(id, now, |session| {
        session
            .sas
            .clone()
            .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))
    })
}
pub fn confirm(id: u64, observed: &str, confirmed: bool, now: u64) -> Result {
    with(id, now, |session| {
        require(
            confirmed && session.sas.as_deref() == Some(observed),
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        session.confirmed = true;
        Ok(())
    })
}
pub fn certificate(id: u64, now: u64) -> Result<Value> {
    with(id, now, |session| {
        require(
            session.role == Role::Approver && session.confirmed,
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        if let Some(value) = &session.certificate {
            return Ok(value.clone());
        }
        let request = session
            .request
            .as_ref()
            .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        for _ in 0..16 {
            let mut certificate = json!({"chain_id":request["chain_id"],"generation":request["generation"],"certificate_id":uuid::Uuid::new_v4().to_string(),
                "device_id":request["device_id"],"device_public_key":request["device_public_key"],"device_kex_public_key":request["device_kex_public_key"],
                "signer_kind":"device","signer_device_id":session.offer["approver_device_id"],"request_hash":object_hash(request),"approved_at_ms":now});
            if crate::recovery::sign(
                &mut certificate,
                &SigningKey::from_bytes(&session.keys.signing),
                CTX_ENROLLMENT,
            )
            .is_err()
            {
                continue;
            }
            wire::validate(&certificate, "certificate")?;
            session.certificate = Some(certificate.clone());
            return Ok(certificate);
        }
        Err(ClientError("C_SIGNATURE_INVALID"))
    })
}
pub fn accept_certificate(id: u64, certificate: &Value, now: u64) -> Result {
    with(id, now, |session| {
        require(
            session.role == Role::Subject && session.confirmed,
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        wire::validate(certificate, "certificate")?;
        let request = session
            .request
            .as_ref()
            .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        require(
            [
                "chain_id",
                "generation",
                "device_id",
                "device_public_key",
                "device_kex_public_key",
            ]
            .iter()
            .all(|key| certificate[*key] == request[*key])
                && certificate["signer_kind"] == "device"
                && certificate["signer_device_id"] == session.offer["approver_device_id"]
                && s(certificate, "request_hash") == object_hash(request),
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        require(
            verify_signed_json(
                s(&session.offer, "approver_device_public_key"),
                s(certificate, "signature"),
                certificate,
                CTX_ENROLLMENT,
            )
            .unwrap_or(false),
            "C_SIGNATURE_INVALID",
        )?;
        require(
            session
                .certificate
                .as_ref()
                .is_none_or(|original| original == certificate),
            "C_DEVICE_EQUIVOCATION",
        )?;
        session.certificate = Some(certificate.clone());
        Ok(())
    })
}

/// Validate and unwrap every transferred same-generation device envelope before it can
/// authorize historical reads or be forwarded in the approval payload (§13.5/§18.1).
pub fn validate_subject_envelopes(
    id: u64,
    authority: &Authority,
    certificate: &Value,
    envelopes: &[Value],
    now: u64,
) -> Result {
    with(id, now, |session| {
        require(
            session.role == Role::Subject
                && session.confirmed
                && session.certificate.as_ref() == Some(certificate),
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        wire::validate(certificate, "certificate")?;
        require(
            !envelopes.is_empty() && envelopes.len() <= 128,
            "C_SCHEMA_VIOLATION",
        )?;
        let chain: [u8; 32] = decode(s(certificate, "chain_id"), Some(32))
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let expected_recipient = hash_bytes(
            &decode(s(certificate, "device_kex_public_key"), Some(32))
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        let mut seen = std::collections::HashSet::new();
        for envelope in envelopes {
            wire::validate(envelope, "envelope")?;
            let number = n(envelope, "epoch") as u32;
            require(seen.insert(number), "C_DEVICE_EQUIVOCATION")?;
            let epoch = epoch_binding(session, authority, number, now)?;
            require(
                envelope["recipient_kind"] == "device"
                    && envelope["recipient_device_id"] == certificate["device_id"]
                    && envelope["chain_id"] == certificate["chain_id"]
                    && envelope["generation"] == certificate["generation"]
                    && envelope["previous_epoch_hash"] == epoch["previous_epoch_hash"]
                    && envelope["epoch_commitment"] == epoch["epoch_commitment"]
                    && envelope["signer_kind"] == "device"
                    && envelope["signer_device_id"] == session.offer["approver_device_id"]
                    && s(envelope, "recipient_key_hash") == expected_recipient,
                "C_BOOTSTRAP_TRUST_REQUIRED",
            )?;
            verify_signed_json(
                s(&session.offer, "approver_device_public_key"),
                s(envelope, "signature"),
                envelope,
                CTX_ENVELOPE,
            )
            .map_err(|_| ClientError("C_SIGNATURE_INVALID"))?;
            unwrap_subject_epoch(session, &chain, envelope, number)?;
        }
        require(
            seen.contains(&(n(&authority.current_epoch()?.record, "epoch") as u32)),
            "C_MISSING_DEPENDENCY",
        )
    })
}

/// Decrypt one already-transferred envelope into typed mutable key material for OS-protected
/// storage. This repeats every binding and signature check at the moment the bytes are released.
pub fn unwrap_subject_envelope(
    id: u64,
    authority: &Authority,
    certificate: &Value,
    envelope: &Value,
    now: u64,
) -> Result<Zeroizing<[u8; 32]>> {
    with(id, now, |session| {
        require(
            session.role == Role::Subject
                && session.confirmed
                && session.certificate.as_ref() == Some(certificate),
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        wire::validate(certificate, "certificate")?;
        wire::validate(envelope, "envelope")?;
        let number = n(envelope, "epoch") as u32;
        let epoch = epoch_binding(session, authority, number, now)?;
        let chain: [u8; 32] = decode(s(certificate, "chain_id"), Some(32))
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let recipient_hash = hash_bytes(
            &decode(s(certificate, "device_kex_public_key"), Some(32))
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        require(
            envelope["recipient_kind"] == "device"
                && envelope["recipient_device_id"] == certificate["device_id"]
                && envelope["chain_id"] == certificate["chain_id"]
                && envelope["generation"] == certificate["generation"]
                && envelope["previous_epoch_hash"] == epoch["previous_epoch_hash"]
                && envelope["epoch_commitment"] == epoch["epoch_commitment"]
                && envelope["signer_kind"] == "device"
                && envelope["signer_device_id"] == session.offer["approver_device_id"]
                && s(envelope, "recipient_key_hash") == recipient_hash,
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        verify_signed_json(
            s(&session.offer, "approver_device_public_key"),
            s(envelope, "signature"),
            envelope,
            CTX_ENVELOPE,
        )
        .map_err(|_| ClientError("C_SIGNATURE_INVALID"))?;
        let typed: crate::models::EpochKeyEnvelope = serde_json::from_value(envelope.clone())
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let secret = Zeroizing::new(
            crypto::unwrap_device_epoch_envelope(&typed, &session.keys.kex, &chain).map_err(
                |error| match error.as_str() {
                    "C_COMMITMENT_MISMATCH" => ClientError("C_COMMITMENT_MISMATCH"),
                    "C_AEAD_FAILURE" => ClientError("C_AEAD_FAILURE"),
                    _ => ClientError("C_SCHEMA_VIOLATION"),
                },
            )?,
        );
        let derived = crypto::derive_epoch_keys(&chain, number, &secret)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        require(
            bool::from(
                derived
                    .epoch_commitment_b64u
                    .as_bytes()
                    .ct_eq(s(envelope, "epoch_commitment").as_bytes()),
            ),
            "C_COMMITMENT_MISMATCH",
        )?;
        Ok(Zeroizing::new(*secret))
    })
}

fn unwrap_subject_epoch(
    session: &Session,
    chain: &[u8; 32],
    envelope: &Value,
    number: u32,
) -> Result<Zeroizing<[u8; 32]>> {
    let typed: crate::models::EpochKeyEnvelope =
        serde_json::from_value(envelope.clone()).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let secret = Zeroizing::new(
        crypto::unwrap_device_epoch_envelope(&typed, &session.keys.kex, chain).map_err(
            |error| match error.as_str() {
                "C_COMMITMENT_MISMATCH" => ClientError("C_COMMITMENT_MISMATCH"),
                "C_AEAD_FAILURE" => ClientError("C_AEAD_FAILURE"),
                _ => ClientError("C_SCHEMA_VIOLATION"),
            },
        )?,
    );
    let derived = crypto::derive_epoch_keys(chain, number, &secret)
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    require(
        bool::from(
            derived
                .epoch_commitment_b64u
                .as_bytes()
                .ct_eq(s(envelope, "epoch_commitment").as_bytes()),
        ),
        "C_COMMITMENT_MISMATCH",
    )?;
    Ok(Zeroizing::new(*secret))
}

/// §18.1: the approver accepts only the same self-signed subject and exact ciphertexts
/// it transferred after its own confirmation. A relay response or a replacement body cannot approve a different device.
pub fn approval_candidate(
    id: u64,
    authority: &Authority,
    submitted: &Value,
    now: u64,
) -> Result<Value> {
    with(id, now, |session| {
        require(
            session.role == Role::Approver && session.confirmed,
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        validate_offer(&session.offer, authority, now)?;
        fields(
            submitted,
            &[
                ("device_record", "object"),
                ("envelopes", "array"),
                ("admin_operation", "object?"),
            ],
            &[],
        )?;
        require(
            submitted["admin_operation"].is_null(),
            "C_RECOVERY_AUTH_REQUIRED",
        )?;
        let record = &submitted["device_record"];
        wire::validate(record, "device")?;
        let certificate = session
            .certificate
            .as_ref()
            .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        require(
            record["enrollment"] == *certificate
                && [
                    "chain_id",
                    "generation",
                    "device_id",
                    "device_public_key",
                    "device_kex_public_key",
                ]
                .iter()
                .all(|key| record[*key] == certificate[*key])
                && record["meta_epoch"] == authority.current_epoch()?.record["epoch"],
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        wire::verify(
            record,
            s(certificate, "device_public_key"),
            CTX_DEVICE_RECORD,
        )?;
        let envelopes = submitted["envelopes"].as_array().unwrap();
        require(
            !envelopes.is_empty() && envelopes.len() <= 128,
            "C_SCHEMA_VIOLATION",
        )?;
        let mut seen = std::collections::HashSet::new();
        for envelope in envelopes {
            wire::validate(envelope, "envelope")?;
            let epoch = n(envelope, "epoch") as u32;
            require(
                seen.insert(epoch) && session.envelopes.get(&epoch) == Some(envelope),
                "C_BOOTSTRAP_TRUST_REQUIRED",
            )?;
        }
        require(
            seen.contains(&(n(&authority.current_epoch()?.record, "epoch") as u32)),
            "C_MISSING_DEPENDENCY",
        )?;
        Ok(submitted.clone())
    })
}

fn epoch_binding<'a>(
    session: &Session,
    authority: &'a Authority,
    number: u32,
    now: u64,
) -> Result<&'a Value> {
    validate_offer(&session.offer, authority, now)?;
    let record = &authority
        .epochs
        .get(&number)
        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?
        .record;
    require(
        record["chain_id"] == session.offer["chain_id"]
            && record["generation"] == session.offer["generation"],
        "C_MISSING_DEPENDENCY",
    )?;
    Ok(record)
}

/// The OS store must supply a previously verified, still eligible epoch secret. Relay retention
/// evidence/handoff is enforced by the lifecycle coordinator; this function never guesses old keys.
pub fn envelope(
    id: u64,
    authority: &Authority,
    number: u32,
    secret: &[u8],
    now: u64,
) -> Result<Value> {
    with(id, now, |session| {
        require(
            session.role == Role::Approver && session.confirmed && session.certificate.is_some(),
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        let epoch = epoch_binding(session, authority, number, now)?;
        let chain: [u8; 32] = decode(s(&session.offer, "chain_id"), Some(32))
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let secret = Zeroizing::new(
            <[u8; 32]>::try_from(secret).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        let keys = crypto::derive_epoch_keys(&chain, number, &secret)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        require(
            bool::from(
                keys.epoch_commitment_b64u
                    .as_bytes()
                    .ct_eq(s(epoch, "epoch_commitment").as_bytes()),
            ),
            "C_COMMITMENT_MISMATCH",
        )?;
        if let Some(original) = session.envelopes.get(&number) {
            return Ok(original.clone());
        }
        require(session.envelopes.len() < 128, "C_LOCAL_LIMIT")?;
        let request = session
            .request
            .as_ref()
            .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        for _ in 0..16 {
            let envelope = crypto::wrap_device_epoch_envelope(
                &uuid::Uuid::new_v4().to_string(),
                s(epoch, "chain_id"),
                n(epoch, "generation") as u32,
                number,
                epoch["previous_epoch_hash"].as_str(),
                s(epoch, "epoch_commitment"),
                s(request, "device_id"),
                s(request, "device_kex_public_key"),
                "device",
                Some(s(&session.offer, "approver_device_id")),
                &SigningKey::from_bytes(&session.keys.signing),
                &secret,
            )
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
            let value =
                serde_json::to_value(envelope).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
            wire::validate(&value, "envelope")?;
            if !verify_signed_json(
                s(&session.offer, "approver_device_public_key"),
                s(&value, "signature"),
                &value,
                CTX_ENVELOPE,
            )
            .unwrap_or(false)
            {
                continue;
            }
            session.envelopes.insert(number, value.clone());
            return Ok(value);
        }
        Err(ClientError("C_SIGNATURE_INVALID"))
    })
}

#[derive(Zeroize, ZeroizeOnDrop)]
pub(crate) struct Material {
    pub record: [u8; 32],
    pub signing: [u8; 32],
    pub kex: [u8; 32],
    pub metadata: [u8; 32],
    pub tag: [u8; 32],
    pub secret: [u8; 32],
}
pub struct PreparedSubject {
    pub public: Value,
    pub(crate) material: Locked<Material>,
}
impl PreparedSubject {
    pub fn epoch_secret(&self) -> &[u8; 32] {
        &self.material.secret
    }
}

/// The subject self-signs after its own confirmation and HPKE/commitment verification (§18.1).
/// Return only typed mutable key material to the OS wrapper, never private JSON. This local bundle
/// is not acceptance evidence; delivery/readback and the relay acknowledgement remain mandatory.
pub fn prepare_subject(
    id: u64,
    authority: &Authority,
    envelope: &Value,
    name: &str,
    protection: &str,
    now: u64,
) -> Result<PreparedSubject> {
    with(id, now, |session| {
        require(
            session.role == Role::Subject && session.confirmed,
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        let certificate = session
            .certificate
            .as_ref()
            .ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        wire::validate(envelope, "envelope")?;
        let current = authority.current_epoch()?;
        let epoch = epoch_binding(session, authority, n(envelope, "epoch") as u32, now)?;
        require(
            epoch == &current.record
                && envelope["recipient_kind"] == "device"
                && envelope["recipient_device_id"] == certificate["device_id"]
                && envelope["chain_id"] == certificate["chain_id"]
                && envelope["generation"] == certificate["generation"]
                && envelope["previous_epoch_hash"] == epoch["previous_epoch_hash"]
                && envelope["epoch_commitment"] == epoch["epoch_commitment"]
                && envelope["signer_kind"] == "device"
                && envelope["signer_device_id"] == certificate["signer_device_id"]
                && s(envelope, "recipient_key_hash")
                    == hash_bytes(
                        &decode(s(certificate, "device_kex_public_key"), Some(32))
                            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
                    ),
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        require(
            verify_signed_json(
                s(&session.offer, "approver_device_public_key"),
                s(envelope, "signature"),
                envelope,
                CTX_ENVELOPE,
            )
            .unwrap_or(false),
            "C_SIGNATURE_INVALID",
        )?;
        require(
            (1..=256).contains(&name.len())
                && matches!(
                    protection,
                    "os-keystore" | "hardware" | "webcrypto-nonextractable" | "passphrase-wrapped"
                ),
            "C_SCHEMA_VIOLATION",
        )?;
        let chain: [u8; 32] = decode(s(certificate, "chain_id"), Some(32))
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let typed = serde_json::from_value(envelope.clone())
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let secret = Zeroizing::new(
            crypto::unwrap_device_epoch_envelope(&typed, &session.keys.kex, &chain).map_err(
                |error| match error.as_str() {
                    "C_COMMITMENT_MISMATCH" => ClientError("C_COMMITMENT_MISMATCH"),
                    "C_AEAD_FAILURE" => ClientError("C_AEAD_FAILURE"),
                    _ => ClientError("C_SCHEMA_VIOLATION"),
                },
            )?,
        );
        let number = n(epoch, "epoch") as u32;
        let keys = crypto::derive_epoch_keys(&chain, number, &secret)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let envelope_hash = object_hash(envelope);
        let record = if let Some((record, original_name, original_protection, original_envelope)) =
            &session.prepared_record
        {
            require(
                name == original_name
                    && protection == original_protection
                    && envelope_hash == *original_envelope,
                "C_DEVICE_EQUIVOCATION",
            )?;
            record.clone()
        } else {
            let mut generated = None;
            for _ in 0..16 {
                let record = crypto::create_device_record(
                    s(certificate, "chain_id"),
                    n(certificate, "generation") as u32,
                    s(certificate, "device_id"),
                    &SigningKey::from_bytes(&session.keys.signing),
                    s(certificate, "device_kex_public_key"),
                    &crate::models::DeviceMetaPlaintext {
                        name: name.into(),
                        key_protection: protection.into(),
                        ext: None,
                    },
                    number,
                    &keys.device_meta_key,
                    serde_json::from_value(certificate.clone())
                        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
                )
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
                let value =
                    serde_json::to_value(record).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
                wire::validate(&value, "device")?;
                if verify_signed_json(
                    s(certificate, "device_public_key"),
                    s(&value, "signature"),
                    &value,
                    CTX_DEVICE_RECORD,
                )
                .unwrap_or(false)
                {
                    generated = Some(value);
                    break;
                }
            }
            let value = generated.ok_or(ClientError("C_SIGNATURE_INVALID"))?;
            session.prepared_record =
                Some((value.clone(), name.into(), protection.into(), envelope_hash));
            value
        };
        let body = json!({"device_record":record,"envelopes":[envelope],"admin_operation":null});
        let ack =
            json!({"epoch_hash":object_hash(epoch),"epoch_commitment":epoch["epoch_commitment"]});
        Ok(PreparedSubject {
            public: json!({"chain_id":certificate["chain_id"],"device_id":certificate["device_id"],
            "device_public_key":certificate["device_public_key"],"device_kex_public_key":certificate["device_kex_public_key"],
            "epoch":number,"epoch_commitment":epoch["epoch_commitment"],"request_body_json":String::from_utf8(jcs::canonicalize_json(&body).map_err(|_|ClientError("C_SCHEMA_VIOLATION"))?).map_err(|_|ClientError("C_SCHEMA_VIOLATION"))?,
            "keys_ack_body_json":String::from_utf8(jcs::canonicalize_json(&ack).map_err(|_|ClientError("C_SCHEMA_VIOLATION"))?).map_err(|_|ClientError("C_SCHEMA_VIOLATION"))?}),
            material: Locked::new(Material {
                record: keys.record_key,
                signing: session.keys.signing,
                kex: session.keys.kex,
                metadata: keys.device_meta_key,
                tag: keys.collection_tag_key,
                secret: *secret,
            })?,
        })
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn independently_generated_python_sas_vector_matches_both_native_endpoints() {
        // Public fixed test inputs, not valid source/relay acceptance evidence.
        let fixture = jcs::parse(include_bytes!("../tests/fixtures/peer-sas.json")).unwrap();
        wire::validate(&fixture["request"], "request").unwrap();
        let approver = Zeroizing::new(std::array::from_fn(|i| i as u8));
        let subject = Zeroizing::new(std::array::from_fn(|i| i as u8 + 32));
        let expected = fixture["expected_sas"].as_str().unwrap();
        assert_eq!(
            sas(
                &fixture["offer"],
                &fixture["request"],
                &approver,
                s(&fixture["request"], "device_kex_public_key")
            )
            .unwrap(),
            expected
        );
        assert_eq!(
            sas(
                &fixture["offer"],
                &fixture["request"],
                &subject,
                s(&fixture["offer"], "approver_device_kex_public_key")
            )
            .unwrap(),
            expected
        );
    }
}
