//! Factory boundary tests with explicitly synthetic receipt evidence. Real relay
//! acceptance and independent cryptography remain separate interoperability tests.
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::{Signer, SigningKey};
use pallasync_core::{
    attestations::{self, RelayPin},
    authority::Authority,
    crypto,
    models::EpochKeyEnvelope,
    root_session::{self, Operation},
    rotation::{self, Request},
    strict::{jcs, verify::*},
};
use serde_json::{Value, json};
use zeroize::Zeroizing;

const PHRASE: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
const ORIGIN: &str = "https://relay.example.com";

fn evidence(state: &Authority, kind: &str, object: Value) -> (Value, Value) {
    let mut entry = json!({"relay_seq":state.sequence+1,"received_at_ms":1791684000000u64,"kind":kind,"object":object});
    if kind == "epoch" {
        entry["committed_at_ms"] = entry["received_at_ms"].clone();
    }
    let key = SigningKey::from_bytes(&std::array::from_fn(|i| 0xa0 + i as u8));
    assert_eq!(
        URL_SAFE_NO_PAD.encode(key.verifying_key().as_bytes()),
        state.pin.public_key
    );
    let mut receipt = json!({"type":"PALLASYNC-UPLOAD-RECEIPT-v3","version":"3.0","receipt_id":uuid::Uuid::new_v4().to_string(),"chain_id":state.pin.chain_id,"log_id":state.pin.log_id,"relay_key_id":state.pin.key_id().unwrap(),"relay_seq":state.sequence+1,"object_hash":object_hash(&entry["object"]),"entry_hash":object_hash(&entry),"previous_head_hash":state.head_hash,"resulting_head_hash":attestations::data_link(&state.head_hash,&entry).unwrap(),"received_at_ms":entry["received_at_ms"],"transparency_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd","transparency_index":state.sequence+1,"transparency_hash":hash_bytes(&[1;32])});
    receipt["relay_signature"] = json!(
        URL_SAFE_NO_PAD.encode(
            key.sign(&signing_bytes(&receipt, CTX_RECEIPT, "relay_signature"))
                .to_bytes()
        )
    );
    (entry, receipt)
}
fn apply(state: Authority, kind: &str, object: Value) -> Authority {
    let (entry, receipt) = evidence(&state, kind, object);
    state
        .advance(&entry, &receipt)
        .unwrap_or_else(|e| panic!("synthetic {kind} at {}: {}", state.sequence + 1, e.0))
}
fn own_genesis() -> (Authority, crypto::GenesisBundle) {
    let bundle =
        crypto::create_genesis_bundle(PHRASE, "", "Factory test", "os-keystore", ORIGIN).unwrap();
    let mut g = jcs::parse(bundle.genesis_request_body_json.as_bytes()).unwrap();
    g["parameters"]["policy"]["scheduled_rotation_ms"] = json!(3_600_000);
    let roots = crypto::derive_root_keys(PHRASE, "").unwrap();
    pallasync_core::recovery::sign(&mut g["parameters"], &roots.admin_key, CTX_PARAMETERS).unwrap();
    let relay = SigningKey::from_bytes(&std::array::from_fn(|i| 0xa0 + i as u8));
    let pin = RelayPin {
        origin: ORIGIN.into(),
        chain_id: bundle.chain_id.clone(),
        public_key: URL_SAFE_NO_PAD.encode(relay.verifying_key().as_bytes()),
        log_id: uuid::Uuid::new_v4().to_string(),
    };
    let mut state = Authority::new(
        pin,
        g["parameters"]["admin_public_key"].as_str().unwrap().into(),
    )
    .unwrap();
    state = apply(state, "parameters", g["parameters"].clone());
    state = apply(state, "device", g["device_record"].clone());
    for env in g["envelopes"].as_array().unwrap() {
        state = apply(state, "envelope", env.clone());
    }
    state = apply(state, "epoch", g["epoch"].clone());
    state.complete_cut().unwrap();
    (state, bundle)
}
fn input(reason: &str, signer: Option<&str>, revoked: Vec<String>) -> Request {
    serde_json::from_value(json!({"reason":reason,"signer_kind":if signer.is_some(){"device"}else{"admin"},"signer_device_id":signer,"revoked_device_ids":revoked,"created_at_ms":1791687600000u64})).unwrap()
}
fn root(state: &Authority, op: Operation) -> u64 {
    root_session::begin(
        PHRASE.as_bytes(),
        b"",
        op,
        Some(&state.pin.chain_id),
        Some(&state.admin_public_key),
    )
    .unwrap()
}
fn check_recovery(prepared: &rotation::PreparedEpoch) {
    let roots = crypto::derive_root_keys(PHRASE, "").unwrap();
    let env: EpochKeyEnvelope = serde_json::from_value(
        prepared.public["request_body"]["envelopes"]
            .as_array()
            .unwrap()
            .iter()
            .find(|v| v["recipient_kind"] == "recovery")
            .unwrap()
            .clone(),
    )
    .unwrap();
    let opened = Zeroizing::new(
        crypto::unwrap_recovery_epoch_envelope(&env, &roots.recovery_kek, &roots.chain_id_digest)
            .unwrap(),
    );
    assert_eq!(opened.as_slice(), prepared.secret());
    assert_eq!(
        object_hash(&serde_json::to_value(env).unwrap()),
        prepared.public["request_body"]["epoch"]["recovery_envelope_hash"]
    );
    let text = prepared.public.to_string();
    for forbidden in [
        "root_seed",
        "recovery_kek",
        "epoch_secret",
        "record_key",
        "metadata_key",
        "device_private",
    ] {
        assert!(!text.contains(forbidden));
    }
}
fn install(mut state: Authority, body: &Value) -> Authority {
    if !body["admin_operation"].is_null() {
        state = state
            .with_action_payload(
                &body["admin_operation"],
                &json!({"epoch":body["epoch"],"envelopes":body["envelopes"]}),
            )
            .unwrap();
    }
    for env in body["envelopes"].as_array().unwrap() {
        state = apply(state, "envelope", env.clone());
    }
    if !body["admin_operation"].is_null() {
        state = apply(state, "admin", body["admin_operation"].clone());
    }
    state = apply(state, "epoch", body["epoch"].clone());
    state.complete_cut().unwrap();
    state
}

#[test]
fn manual_and_scheduled_proposals_open_both_envelopes_and_use_fresh_secrets() {
    let (state, bundle) = own_genesis();
    let handle = root(&state, Operation::Rotate);
    let seed = Zeroizing::new(decode(&bundle.device_signing_key, Some(32)).unwrap());
    let first = rotation::prepare(
        handle,
        Operation::Rotate,
        &state,
        input("manual", Some(&bundle.device_id), vec![]),
        &seed,
    )
    .unwrap();
    let mut early = input("scheduled", Some(&bundle.device_id), vec![]);
    early.created_at_ms -= 1;
    assert_eq!(
        rotation::prepare(handle, Operation::Rotate, &state, early, &seed)
            .err()
            .unwrap()
            .0,
        "C_MISSING_DEPENDENCY"
    );
    let second = rotation::prepare(
        handle,
        Operation::Rotate,
        &state,
        input("scheduled", Some(&bundle.device_id), vec![]),
        &seed,
    )
    .unwrap();
    assert_ne!(first.secret(), second.secret());
    assert_ne!(first.public["epoch_hash"], second.public["epoch_hash"]);
    for prepared in [&first, &second] {
        check_recovery(prepared);
        assert!(prepared.public["request_body"]["admin_operation"].is_null());
        let env: EpochKeyEnvelope =
            serde_json::from_value(prepared.public["request_body"]["envelopes"][0].clone())
                .unwrap();
        let private = Zeroizing::new(
            <[u8; 32]>::try_from(decode(&bundle.device_kex_private_key, Some(32)).unwrap())
                .unwrap(),
        );
        let opened = Zeroizing::new(
            crypto::unwrap_device_epoch_envelope(
                &env,
                &private,
                &decode(&state.pin.chain_id, Some(32))
                    .unwrap()
                    .try_into()
                    .unwrap(),
            )
            .unwrap(),
        );
        assert_eq!(opened.as_slice(), prepared.secret());
        let body = &prepared.public["request_body"];
        let (entry, receipt) = evidence(&state, "envelope", body["envelopes"][0].clone());
        let mut after = state.advance(&entry, &receipt).unwrap();
        for envelope in body["envelopes"].as_array().unwrap().iter().skip(1) {
            after = apply(after, "envelope", envelope.clone());
        }
        after = apply(after, "epoch", body["epoch"].clone());
        after.complete_cut().unwrap();
        assert_eq!(after.current_epoch().unwrap().record["epoch"], 1);
        let delivered = pallasync_core::epoch_session::open_own_envelope(
            &after,
            &entry,
            &receipt,
            &bundle.device_id,
            &*private,
        )
        .unwrap();
        assert_eq!(delivered.as_slice(), prepared.secret());
        assert_eq!(
            pallasync_core::epoch_session::open_own_envelope(
                &after,
                &entry,
                &receipt,
                &bundle.device_id,
                &[4; 32]
            )
            .unwrap_err()
            .0,
            "C_BOOTSTRAP_TRUST_REQUIRED"
        );
        let mut changed = entry.clone();
        changed["object"]["ciphertext"] = json!(URL_SAFE_NO_PAD.encode([1; 48]));
        assert_eq!(
            pallasync_core::epoch_session::open_own_envelope(
                &after,
                &changed,
                &receipt,
                &bundle.device_id,
                &*private
            )
            .unwrap_err()
            .0,
            "C_SIGNATURE_INVALID"
        );
    }
    root_session::end(handle);
}

#[test]
fn an_admin_revocation_binds_exact_parameters_epoch_and_surviving_members() {
    let f = jcs::parse(include_bytes!("fixtures/python-genesis-acceptance.json")).unwrap();
    let mut state = Authority::from_genesis(
        &f["created"],
        &f["genesis"],
        &f["identity"],
        f["origin"].as_str().unwrap(),
        f["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    let enrol = &f["enrollment"];
    state = state.with_action_payload(&enrol["body"]["admin_operation"], &json!({"device_record":enrol["body"]["device_record"],"envelopes":enrol["body"]["envelopes"]})).unwrap();
    state = apply(state, "device", enrol["body"]["device_record"].clone());
    for env in enrol["body"]["envelopes"].as_array().unwrap() {
        state = apply(state, "envelope", env.clone());
    }
    state = apply(state, "admin", enrol["body"]["admin_operation"].clone());
    let joined = enrol["body"]["device_record"]["device_id"]
        .as_str()
        .unwrap();
    assert!(
        !state.current_epoch().unwrap().record["members"]
            .as_array()
            .unwrap()
            .iter()
            .any(|id| id == joined)
    );
    state.current_key_recipient(joined).unwrap();
    let revoked = f["genesis"]["device_record"]["device_id"].as_str().unwrap();
    let handle = root(&state, Operation::Revoke);
    let prepared = rotation::prepare(
        handle,
        Operation::Revoke,
        &state,
        input("device_revocation", None, vec![revoked.into()]),
        &[],
    )
    .unwrap();
    check_recovery(&prepared);
    let body = &prepared.public["request_body"];
    assert_eq!(
        body["epoch"]["members"],
        json!([enrol["body"]["device_record"]["device_id"]])
    );
    let after = install(state.clone(), body);
    assert!(after.devices[revoked].revoked_seq.is_some());
    after.current_key_recipient(joined).unwrap();
    assert_eq!(
        after.current_key_recipient(revoked).unwrap_err().0,
        "C_RECOVERY_AUTH_REQUIRED"
    );
    assert!(state.devices[revoked].revoked_seq.is_none());
    root_session::end(handle);
}

#[test]
fn policy_wrong_private_identity_and_cancel_never_create_a_proposal() {
    let (state, bundle) = own_genesis();
    let handle = root(&state, Operation::Rotate);
    let wrong = rotation::prepare(
        handle,
        Operation::Rotate,
        &state,
        input("manual", Some(&bundle.device_id), vec![]),
        &[1; 32],
    );
    assert_eq!(wrong.err().unwrap().0, "C_BOOTSTRAP_TRUST_REQUIRED");
    let mut blocked = state.clone();
    blocked.parameters.as_mut().unwrap()["policy"]["allow_device_rotation"] = json!(false);
    let seed = Zeroizing::new(decode(&bundle.device_signing_key, Some(32)).unwrap());
    assert_eq!(
        rotation::prepare(
            handle,
            Operation::Rotate,
            &blocked,
            input("manual", Some(&bundle.device_id), vec![]),
            &seed
        )
        .err()
        .unwrap()
        .0,
        "C_RECOVERY_AUTH_REQUIRED"
    );
    root_session::end(handle);
    assert_eq!(
        rotation::prepare(
            handle,
            Operation::Rotate,
            &state,
            input("manual", Some(&bundle.device_id), vec![]),
            &seed
        )
        .err()
        .unwrap()
        .0,
        "C_RECOVERY_AUTH_REQUIRED"
    );
    assert_eq!(state.current_epoch().unwrap().record["epoch"], 0);
    let mut missing_null =
        json!({"reason":"manual","signer_kind":"device","revoked_device_ids":[],"created_at_ms":0});
    assert!(serde_json::from_value::<Request>(missing_null.clone()).is_err());
    missing_null["signer_device_id"] = Value::Null;
    missing_null["unknown"] = json!(true);
    assert!(serde_json::from_value::<Request>(missing_null).is_err());
}
