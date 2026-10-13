//! §7.11.5 local-only transport; live confirmation precedes every secret packet.
use crate::{
    client_error::{ClientError, Result},
    locked_memory::Locked,
    public_wire::{fields, n, s},
    recovery::{self, SecretJson},
    strict::verify::*,
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::SigningKey;
use hpke::{
    Deserializable, Kem, OpModeR, OpModeS, Serializable, aead::ChaCha20Poly1305, kdf::HkdfSha256,
    kem::X25519HkdfSha256,
};
use rand_core::OsRng;
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use std::{
    collections::{HashMap, HashSet},
    sync::{Mutex, OnceLock},
    time::{Duration, Instant},
};
use x25519_dalek::{PublicKey, StaticSecret};
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

const CTX_DESCRIPTOR: &[u8] = b"PALLASYNC-RECOVERY-SESSION-v3\0";
const CTX_TRANSFER: &[u8] = b"PALLASYNC-RECOVERY-TRANSFER-v3\0";
const TTL: Duration = Duration::from_secs(300);
#[derive(Zeroize, ZeroizeOnDrop)]
struct SessionKeys {
    signing: [u8; 32],
    kex: [u8; 32],
}
struct Session {
    lease: crate::secret_lifetime::Lease,
    descriptor: Value,
    peer: Option<Value>,
    keys: Locked<SessionKeys>,
    established: Instant,
    confirmed: bool,
    sent: u64,
    received: u64,
    incoming: HashMap<u64, (String, Value)>,
    received_ids: HashSet<String>,
    outgoing: HashMap<u64, Value>,
    admin: String,
}
static SESSIONS: OnceLock<Mutex<HashMap<u64, Session>>> = OnceLock::new();
fn sessions() -> &'static Mutex<HashMap<u64, Session>> {
    SESSIONS.get_or_init(|| Mutex::new(HashMap::new()))
}
fn invalid() -> ClientError {
    ClientError("C_RECOVERY_SHARE_INVALID")
}
fn check(ok: bool) -> Result {
    if ok { Ok(()) } else { Err(invalid()) }
}
pub fn descriptor_valid(v: &Value) -> Result {
    fields(
        v,
        &[
            ("type", "PALLASYNC-RECOVERY-SESSION-v3"),
            ("version", "3.0"),
            ("ceremony_id", "uuid"),
            ("session_id", "uuid"),
            ("endpoint_id", "uuid"),
            ("side", "initiator|responder"),
            ("role", "dealer|holder|reader|collector"),
            ("purpose", "create|rotate|recover|disable"),
            ("chain_id", "b32"),
            ("generation", "u32"),
            ("recovery_set_id", "uuid"),
            ("set_manifest_hash", "b32"),
            ("session_nonce", "b32"),
            ("session_signing_public_key", "b32"),
            ("session_kex_public_key", "b32"),
            ("ttl_ms", "int"),
            ("signature", "b64"),
        ],
        &[("ext", "object")],
    )
    .map_err(|_| invalid())?;
    if let Some(extension) = v.get("ext") {
        crate::schema::extension(extension).map_err(|_| invalid())?;
    }
    check(
        n(v, "ttl_ms") == 300_000
            && valid_ed_key(s(v, "session_signing_public_key"))
            && valid_x_key(s(v, "session_kex_public_key")),
    )?;
    recovery::verify(v, s(v, "session_signing_public_key"), CTX_DESCRIPTOR)
}
#[allow(clippy::too_many_arguments)]
pub fn begin(
    manifest: &Value,
    admin: &str,
    ceremony_id: &str,
    session_id: &str,
    side: &str,
    role: &str,
    purpose: &str,
) -> Result<(u64, Value)> {
    let lease = crate::secret_lifetime::lease()?;
    crate::secret_lifetime::start();
    recovery::validate_manifest(manifest, admin)?;
    let (signing, private) = crate::crypto::generate_device_keys();
    let private = Zeroizing::new(private);
    let kex = StaticSecret::from(*private);
    let mut descriptor = json!({"type":"PALLASYNC-RECOVERY-SESSION-v3","version":"3.0","ceremony_id":ceremony_id,"session_id":session_id,"endpoint_id":uuid::Uuid::new_v4().to_string(),"side":side,"role":role,"purpose":purpose,"chain_id":manifest["chain_id"],"generation":manifest["generation"],"recovery_set_id":manifest["recovery_set_id"],"set_manifest_hash":object_hash(manifest),"session_nonce":URL_SAFE_NO_PAD.encode(crate::crypto::generate_epoch_secret()),"session_signing_public_key":URL_SAFE_NO_PAD.encode(signing.verifying_key().as_bytes()),"session_kex_public_key":URL_SAFE_NO_PAD.encode(PublicKey::from(&kex).as_bytes()),"ttl_ms":300000});
    recovery::sign(&mut descriptor, &signing, CTX_DESCRIPTOR)?;
    descriptor_valid(&descriptor)?;
    let mut map = sessions().lock().map_err(|_| invalid())?;
    lease.check()?;
    let mut expired = Vec::new();
    map.retain(|handle, s| {
        let live = s.lease.live() && s.established.elapsed() < TTL;
        if !live {
            expired.push(*handle);
        }
        live
    });
    // Removing a session must also release the holder's dedicated RSWK pages. Otherwise
    // a later reaper cannot find that already-removed handle to clean its holder state.
    for handle in expired {
        crate::recovery_flow::discard_holder(handle);
    }
    let mut handle = rand::random::<u64>() & i64::MAX as u64;
    while handle == 0 || map.contains_key(&handle) {
        handle = rand::random::<u64>() & i64::MAX as u64;
    }
    map.insert(
        handle,
        Session {
            lease,
            descriptor: descriptor.clone(),
            peer: None,
            keys: Locked::new(SessionKeys {
                signing: signing.to_bytes(),
                kex: *private,
            })?,
            established: Instant::now(),
            confirmed: false,
            sent: 0,
            received: 0,
            incoming: HashMap::new(),
            received_ids: HashSet::new(),
            outgoing: HashMap::new(),
            admin: admin.into(),
        },
    );
    Ok((handle, descriptor))
}
fn with<T>(handle: u64, f: impl FnOnce(&mut Session) -> Result<T>) -> Result<T> {
    for expired in expire() {
        crate::recovery_flow::discard_holder(expired);
    }
    let mut map = sessions().lock().map_err(|_| invalid())?;
    let session = map
        .get_mut(&handle)
        .ok_or(ClientError("C_RECOVERY_SESSION_EXPIRED"))?;
    let result = f(session);
    if session.lease.live() && session.established.elapsed() < TTL {
        result
    } else {
        map.remove(&handle);
        drop(map);
        crate::recovery_flow::discard_holder(handle);
        Err(ClientError("C_RECOVERY_SESSION_EXPIRED"))
    }
}
pub fn end(handle: u64) {
    if let Ok(mut map) = sessions().lock() {
        map.remove(&handle);
    }
}
#[cfg(test)]
pub(crate) fn force_pair_expiry_for_test(handle: u64) {
    if let Ok(mut map) = sessions().lock()
        && let Some(session) = map.get_mut(&handle)
    {
        session.established = Instant::now() - TTL - Duration::from_secs(1);
    }
}
pub fn end_all() {
    if let Ok(mut map) = sessions().lock() {
        map.clear();
    }
}
pub(crate) fn invalidate_recovery_set(set_id: &str) {
    let mut removed = Vec::new();
    if let Ok(mut map) = sessions().lock() {
        map.retain(|handle, session| {
            let keep = session.descriptor["recovery_set_id"] != set_id;
            if !keep {
                removed.push(*handle);
            }
            keep
        });
    }
    for handle in removed {
        crate::recovery_flow::discard_holder(handle);
    }
}
pub(crate) fn expire() -> Vec<u64> {
    let mut expired = Vec::new();
    if let Ok(mut map) = sessions().lock() {
        map.retain(|h, s| {
            let live = s.lease.live() && s.established.elapsed() < TTL;
            if !live {
                expired.push(*h);
            }
            live
        });
    }
    expired
}
pub(crate) fn context(handle: u64) -> Result<Value> {
    with(handle, |s| Ok(s.descriptor.clone()))
}
fn sas(session: &Session) -> Result<String> {
    let peer = session
        .peer
        .as_ref()
        .ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
    let key: [u8; 32] = decode(s(peer, "session_kex_public_key"), Some(32))
        .map_err(|_| invalid())?
        .try_into()
        .map_err(|_| invalid())?;
    let dh = Zeroizing::new(
        StaticSecret::from(session.keys.kex)
            .diffie_hellman(&PublicKey::from(key))
            .to_bytes(),
    );
    check(dh.iter().any(|b| *b != 0))?;
    let (initiator, responder) = if session.descriptor["side"] == "initiator" {
        (&session.descriptor, peer)
    } else {
        (peer, &session.descriptor)
    };
    let mut hash = Sha256::new();
    hash.update(b"PALLASYNC-RECOVERY-SAS-v3\0");
    hash.update(dh.as_slice());
    hash.update(recovery::canonical(
        &json!({"initiator":initiator,"responder":responder}),
    )?);
    let hex = hex::encode(&hash.finalize()[..6]);
    Ok(format!("{} {} {}", &hex[..4], &hex[4..8], &hex[8..]))
}
pub fn bind_peer(handle: u64, peer: &Value) -> Result<String> {
    descriptor_valid(peer)?;
    with(handle, |session| {
        let own = &session.descriptor;
        for k in [
            "ceremony_id",
            "session_id",
            "purpose",
            "chain_id",
            "generation",
            "recovery_set_id",
            "set_manifest_hash",
        ] {
            check(own[k] == peer[k])?;
        }
        check(
            own["side"] != peer["side"]
                && own["endpoint_id"] != peer["endpoint_id"]
                && own["session_signing_public_key"] != peer["session_signing_public_key"]
                && own["session_kex_public_key"] != peer["session_kex_public_key"],
        )?;
        if let Some(previous) = &session.peer {
            check(previous == peer)?;
        } else {
            session.peer = Some(peer.clone());
            session.established = Instant::now();
        }
        sas(session)
    })
}
/// Invoked only by the foreground confirmation screen after both physical endpoints confirm.
pub fn confirm(handle: u64, displayed_sas: &str, both_endpoints_confirmed: bool) -> Result {
    let result = with(handle, |session| {
        if !both_endpoints_confirmed {
            return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
        }
        if sas(session)? != displayed_sas {
            return Err(ClientError("C_RECOVERY_SAS_MISMATCH"));
        }
        session.confirmed = true;
        Ok(())
    });
    if result.is_err() {
        crate::recovery_flow::end(handle);
    }
    result
}
fn direction(sender: &Value, recipient: &Value, kind: &str) -> Result {
    let pair = (s(sender, "role"), s(recipient, "role"));
    check(match kind {
        "distribute_share" | "signed_share" => matches!(pair, ("dealer", "holder" | "reader")),
        "wrap_proposal" | "persisted" => matches!(pair, ("holder" | "reader", "dealer")),
        "collect_share" => matches!(pair, ("holder" | "reader", "collector")),
        "result" => true,
        _ => false,
    })
}
fn payload_valid(session: &Session, kind: &str, p: &Value) -> Result {
    match kind {
        "distribute_share" => {
            fields(
                p,
                &[
                    ("manifest", "object"),
                    ("blob", "object"),
                    ("share_index", "int"),
                    ("raw_share", "b32"),
                ],
                &[],
            )
            .map_err(|_| invalid())?;
            recovery::validate_manifest(&p["manifest"], &session.admin)?;
            recovery::validate_blob(&p["blob"])?;
            check(
                object_hash(&p["manifest"]) == s(&session.descriptor, "set_manifest_hash")
                    && object_hash(&p["blob"]) == s(&p["manifest"], "recovery_blob_hash")
                    && (1..=n(&p["manifest"], "total_shares")).contains(&n(p, "share_index")),
            )?;
            let piece = recovery::decode_piece(n(p, "share_index") as u8, s(p, "raw_share"))?;
            let h = &p["manifest"]["holders"][usize::from(piece.index) - 1];
            check(
                recovery::commitment(s(&p["manifest"], "recovery_set_id"), &piece)?
                    == s(h, "share_commitment"),
            )?;
        }
        "wrap_proposal" | "signed_share" => {
            fields(p, &[("record", "object")], &[]).map_err(|_| invalid())?;
            recovery::validate_share(&p["record"], kind == "signed_share")?;
            check(
                s(&p["record"], "set_manifest_hash") == s(&session.descriptor, "set_manifest_hash")
                    && s(&p["record"], "admin_public_key") == session.admin
                    && p["record"]["chain_id"] == session.descriptor["chain_id"]
                    && p["record"]["generation"] == session.descriptor["generation"]
                    && p["record"]["recovery_set_id"] == session.descriptor["recovery_set_id"],
            )?;
        }
        "persisted" => {
            fields(
                p,
                &[
                    ("recovery_share_id", "uuid"),
                    ("record_hash", "b32"),
                    ("verified", "bool"),
                ],
                &[],
            )
            .map_err(|_| invalid())?;
        }
        "collect_share" => {
            fields(p, &[("package", "object"), ("raw_share", "b32")], &[])
                .map_err(|_| invalid())?;
            recovery::validate_package(&p["package"], &session.admin, None)?;
            check(
                object_hash(&p["package"]["manifest"])
                    == s(&session.descriptor, "set_manifest_hash"),
            )?;
            let r = &p["package"]["record"];
            let piece = recovery::decode_piece(n(r, "share_index") as u8, s(p, "raw_share"))?;
            check(
                recovery::commitment(s(r, "recovery_set_id"), &piece)? == s(r, "share_commitment"),
            )?;
        }
        "result" => {
            fields(
                p,
                &[("result", "succeeded|failed|cancelled"), ("code", "text?")],
                &[],
            )
            .map_err(|_| invalid())?;
            check(p["code"].is_null() || crate::error_catalogue::known(s(p, "code")))?;
        }
        _ => return Err(invalid()),
    }
    Ok(())
}
pub(crate) fn encrypt(handle: u64, kind: &str, payload: &Value) -> Result<Value> {
    with(handle, |session| encrypt_locked(session, kind, payload))
}
fn encrypt_locked(session: &mut Session, kind: &str, payload: &Value) -> Result<Value> {
    if !session.confirmed {
        return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
    }
    let peer = session
        .peer
        .as_ref()
        .ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
    direction(&session.descriptor, peer, kind)?;
    payload_valid(session, kind, payload)?;
    if session.sent == crate::strict::jcs::MAX_INTEGER {
        return Err(ClientError("C_COUNTER_EXHAUSTED"));
    }
    let header = json!({"type":"PALLASYNC-RECOVERY-TRANSFER-v3","version":"3.0","transfer_id":uuid::Uuid::new_v4().to_string(),"session_id":session.descriptor["session_id"],"sender_descriptor_hash":object_hash(&session.descriptor),"recipient_descriptor_hash":object_hash(peer),"message_seq":session.sent+1,"message_kind":kind});
    let aad = recovery::canonical(&header)?;
    let mut info = b"PALLASYNC-RECOVERY-TRANSFER-HPKE-v3\0".to_vec();
    info.extend_from_slice(&aad);
    let pk = <X25519HkdfSha256 as Kem>::PublicKey::from_bytes(
        &decode(s(peer, "session_kex_public_key"), Some(32)).map_err(|_| invalid())?,
    )
    .map_err(|_| invalid())?;
    let plain = Zeroizing::new(recovery::canonical(payload)?);
    check(plain.len() <= recovery::MAX_PACKAGE)?;
    let (enc, ciphertext) = hpke::single_shot_seal::<
        ChaCha20Poly1305,
        HkdfSha256,
        X25519HkdfSha256,
        _,
    >(&OpModeS::Base, &pk, &info, &plain, &aad, &mut OsRng)
    .map_err(|_| invalid())?;
    let mut packet = header;
    packet["enc"] = json!(URL_SAFE_NO_PAD.encode(enc.to_bytes()));
    packet["ciphertext"] = json!(URL_SAFE_NO_PAD.encode(ciphertext));
    recovery::sign(
        &mut packet,
        &SigningKey::from_bytes(&session.keys.signing),
        CTX_TRANSFER,
    )?;
    check(recovery::canonical(&packet)?.len() <= recovery::MAX_PACKAGE)?;
    session.sent += 1;
    session.outgoing.insert(session.sent, packet.clone());
    Ok(packet)
}
pub(crate) enum Reply {
    Transfer(&'static str, Value),
    Public(Value),
}
pub(crate) fn receive(
    handle: u64,
    bytes: &[u8],
    process: impl FnOnce(&str, &Value) -> Result<Reply>,
) -> Result<Value> {
    let packet = recovery::parse(bytes)?;
    fields(
        &packet,
        &[
            ("type", "PALLASYNC-RECOVERY-TRANSFER-v3"),
            ("version", "3.0"),
            ("transfer_id", "uuid"),
            ("session_id", "uuid"),
            ("sender_descriptor_hash", "b32"),
            ("recipient_descriptor_hash", "b32"),
            ("message_seq", "int"),
            (
                "message_kind",
                "distribute_share|wrap_proposal|signed_share|persisted|collect_share|result",
            ),
            ("enc", "b32"),
            ("ciphertext", "text"),
            ("signature", "b64"),
        ],
        &[("ext", "object")],
    )
    .map_err(|_| invalid())?;
    if let Some(extension) = packet.get("ext") {
        crate::schema::extension(extension).map_err(|_| invalid())?;
    }
    with(handle, |session| {
        if !session.confirmed {
            return Err(ClientError("C_RECOVERY_AUTH_REQUIRED"));
        }
        let peer = session
            .peer
            .as_ref()
            .ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?;
        check(
            packet["session_id"] == session.descriptor["session_id"]
                && s(&packet, "sender_descriptor_hash") == object_hash(peer)
                && s(&packet, "recipient_descriptor_hash") == object_hash(&session.descriptor)
                && valid_x_key(s(&packet, "enc")),
        )?;
        recovery::verify(&packet, s(peer, "session_signing_public_key"), CTX_TRANSFER)?;
        let seq = n(&packet, "message_seq");
        let hash = hash_bytes(bytes);
        if let Some((previous, response)) = session.incoming.get(&seq) {
            check(previous == &hash)?;
            return Ok(response.clone());
        }
        let kind = s(&packet, "message_kind");
        direction(peer, &session.descriptor, kind)?;
        check(!session.received_ids.contains(s(&packet, "transfer_id")))?;
        if seq > session.received + 1 {
            return Err(ClientError("C_MISSING_DEPENDENCY"));
        }
        check(
            seq == session.received + 1
                && !session.received_ids.contains(s(&packet, "transfer_id")),
        )?;
        let mut header = packet.clone();
        let o = header.as_object_mut().unwrap();
        for k in ["enc", "ciphertext", "signature"] {
            o.remove(k);
        }
        let aad = recovery::canonical(&header)?;
        let mut info = b"PALLASYNC-RECOVERY-TRANSFER-HPKE-v3\0".to_vec();
        info.extend_from_slice(&aad);
        let sk = <X25519HkdfSha256 as Kem>::PrivateKey::from_bytes(&session.keys.kex)
            .map_err(|_| invalid())?;
        let enc = <X25519HkdfSha256 as Kem>::EncappedKey::from_bytes(
            &decode(s(&packet, "enc"), Some(32)).map_err(|_| invalid())?,
        )
        .map_err(|_| invalid())?;
        let plain = Zeroizing::new(
            hpke::single_shot_open::<ChaCha20Poly1305, HkdfSha256, X25519HkdfSha256>(
                &OpModeR::Base,
                &sk,
                &enc,
                &info,
                &decode(s(&packet, "ciphertext"), None).map_err(|_| invalid())?,
                &aad,
            )
            .map_err(|_| invalid())?,
        );
        let payload = SecretJson(recovery::parse(&plain)?);
        check(plain.as_slice() == recovery::canonical(&payload.0)?)?;
        payload_valid(session, kind, &payload.0)?;
        let result = match process(kind, &payload.0)? {
            Reply::Transfer(kind, payload) => encrypt_locked(session, kind, &payload)?,
            Reply::Public(value) => value,
        };
        session.received = seq;
        session
            .received_ids
            .insert(s(&packet, "transfer_id").into());
        session.incoming.insert(seq, (hash, result.clone()));
        Ok(result)
    })
}
pub fn retry_packet(handle: u64, sequence: u64) -> Result<Value> {
    with(handle, |s| {
        s.outgoing.get(&sequence).cloned().ok_or_else(invalid)
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn signed_descriptor_extensions_are_covered_by_sas_and_signature_without_new_semantics() {
        let manifest = recovery::parse(include_bytes!(
            "../tests/fixtures/recovery-manifest-c8.json"
        ))
        .unwrap();
        let admin = manifest["admin_public_key"].as_str().unwrap();
        let ceremony = uuid::Uuid::new_v4().to_string();
        let session = uuid::Uuid::new_v4().to_string();
        let (sender, _) = begin(
            &manifest,
            admin,
            &ceremony,
            &session,
            "initiator",
            "dealer",
            "create",
        )
        .unwrap();
        let (receiver, rd) = begin(
            &manifest,
            admin,
            &ceremony,
            &session,
            "responder",
            "holder",
            "create",
        )
        .unwrap();
        let sd = with(sender, |state| {
            state.descriptor["ext"] = json!({"org.example.flag":true});
            recovery::sign(
                &mut state.descriptor,
                &ed25519_dalek::SigningKey::from_bytes(&state.keys.signing),
                CTX_DESCRIPTOR,
            )?;
            Ok(state.descriptor.clone())
        })
        .unwrap();
        descriptor_valid(&sd).unwrap();
        let mut changed = sd.clone();
        changed["ext"]["org.example.flag"] = json!(false);
        assert!(descriptor_valid(&changed).is_err());
        let sas = bind_peer(sender, &rd).unwrap();
        assert_eq!(sas, bind_peer(receiver, &sd).unwrap());
        confirm(sender, &sas, true).unwrap();
        confirm(receiver, &sas, true).unwrap();
        let packet = encrypt(sender, "result", &json!({"result":"succeeded","code":null})).unwrap();
        assert_eq!(
            receive(
                receiver,
                &recovery::canonical(&packet).unwrap(),
                |_, payload| Ok(Reply::Public(payload.clone()))
            )
            .unwrap()["result"],
            "succeeded"
        );
        end(sender);
        end(receiver);
    }
    #[test]
    fn verified_future_packet_waits_for_exact_predecessor_without_processing_or_losing_pair() {
        let manifest = recovery::parse(include_bytes!(
            "../tests/fixtures/recovery-manifest-c8.json"
        ))
        .unwrap();
        let admin = manifest["admin_public_key"].as_str().unwrap();
        let ceremony = uuid::Uuid::new_v4().to_string();
        let session = uuid::Uuid::new_v4().to_string();
        let (sender, sd) = begin(
            &manifest,
            admin,
            &ceremony,
            &session,
            "initiator",
            "dealer",
            "create",
        )
        .unwrap();
        let (receiver, rd) = begin(
            &manifest,
            admin,
            &ceremony,
            &session,
            "responder",
            "holder",
            "create",
        )
        .unwrap();
        let sas = bind_peer(sender, &rd).unwrap();
        assert_eq!(sas, bind_peer(receiver, &sd).unwrap());
        confirm(sender, &sas, true).unwrap();
        confirm(receiver, &sas, true).unwrap();
        let payload = json!({"result":"succeeded","code":null});
        let first = encrypt(sender, "result", &payload).unwrap();
        let second = encrypt(sender, "result", &payload).unwrap();
        assert_eq!(
            receive(
                receiver,
                &recovery::canonical(&second).unwrap(),
                |_, _| panic!("Gap must not process")
            )
            .err()
            .unwrap()
            .0,
            "C_MISSING_DEPENDENCY"
        );
        assert_eq!(retry_packet(sender, 1).unwrap(), first);
        for packet in [first, second] {
            let bytes = recovery::canonical(&packet).unwrap();
            let ack = receive(receiver, &bytes, |_, _| {
                Ok(Reply::Public(json!({"processed":true})))
            })
            .unwrap();
            assert_eq!(
                receive(receiver, &bytes, |_, _| panic!("Replay must not process")).unwrap(),
                ack
            );
        }
        end(sender);
        end(receiver);
    }
}
