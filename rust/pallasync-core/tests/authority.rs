use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::SigningKey;
use pallasync_core::{
    attestations::RelayPin,
    authority::Authority,
    recovery,
    strict::{jcs, verify::*},
};
use serde_json::{Value, json};

fn fixture() -> Value {
    jcs::parse(include_bytes!("fixtures/python-genesis-acceptance.json")).unwrap()
}
fn genesis() -> Authority {
    let f = fixture();
    let pin = RelayPin {
        origin: f["origin"].as_str().unwrap().into(),
        chain_id: f["identity"]["chain_id"].as_str().unwrap().into(),
        public_key: f["relay_public_key"].as_str().unwrap().into(),
        log_id: f["created"]["log_id"].as_str().unwrap().into(),
    };
    let mut state = Authority::new(
        pin,
        f["identity"]["admin_public_key"].as_str().unwrap().into(),
    )
    .unwrap();
    let g = &f["genesis"];
    let device_env = g["envelopes"]
        .as_array()
        .unwrap()
        .iter()
        .find(|v| v["recipient_kind"] == "device")
        .unwrap();
    let recovery_env = g["envelopes"]
        .as_array()
        .unwrap()
        .iter()
        .find(|v| v["recipient_kind"] == "recovery")
        .unwrap();
    for (index, (kind, object)) in [
        ("parameters", &g["parameters"]),
        ("device", &g["device_record"]),
        ("envelope", device_env),
        ("envelope", recovery_env),
        ("epoch", &g["epoch"]),
    ]
    .iter()
    .enumerate()
    {
        let receipt = &f["created"]["upload_receipts"][index];
        let mut entry = json!({"relay_seq":index+1,"received_at_ms":receipt["received_at_ms"],"kind":kind,"object":object});
        if *kind == "epoch" {
            entry["committed_at_ms"] = receipt["received_at_ms"].clone();
        }
        state = state.advance(&entry, receipt).unwrap();
    }
    assert_eq!(state.head_hash, f["created"]["head_hash"].as_str().unwrap());
    state
}
fn accepted(state: &Authority, kind: &str, object: Value) -> (Value, Value) {
    let mut entry = json!({"relay_seq":state.sequence+1,"received_at_ms":1791684000000u64,"kind":kind,"object":object});
    if kind == "epoch" {
        entry["committed_at_ms"] = entry["received_at_ms"].clone();
    }
    let mut receipt = json!({"type":"PALLASYNC-UPLOAD-RECEIPT-v3","version":"3.0","receipt_id":uuid::Uuid::new_v4().to_string(),"chain_id":state.pin.chain_id,"log_id":state.pin.log_id,"relay_key_id":state.pin.key_id().unwrap(),"relay_seq":state.sequence+1,"object_hash":object_hash(&entry["object"]),"entry_hash":object_hash(&entry),"previous_head_hash":state.head_hash,"resulting_head_hash":pallasync_core::attestations::data_link(&state.head_hash,&entry).unwrap(),"received_at_ms":entry["received_at_ms"],"transparency_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd","transparency_index":state.sequence+1,"transparency_hash":hash_bytes(&[1;32])});
    let seed = std::array::from_fn(|i| 0xa0 + i as u8);
    let key = SigningKey::from_bytes(&seed);
    assert_eq!(
        URL_SAFE_NO_PAD.encode(key.verifying_key().as_bytes()),
        state.pin.public_key
    );
    use ed25519_dalek::Signer;
    receipt["relay_signature"] = json!(
        URL_SAFE_NO_PAD.encode(
            key.sign(&signing_bytes(&receipt, CTX_RECEIPT, "relay_signature"))
                .to_bytes()
        )
    );
    (entry, receipt)
}
#[test]
fn verified_genesis_authorizes_exact_identity_and_never_resets_a_prefix() {
    let state = genesis();
    assert_eq!(state.current_epoch().unwrap().record["epoch"], 0);
    state.record_eligible(&fixture()["record"], 6).unwrap();
    let mut bad = fixture()["record"].clone();
    bad["device_id"] = json!(uuid::Uuid::new_v4().to_string());
    assert_eq!(
        state.record_eligible(&bad, 6).err().unwrap().0,
        "C_MISSING_DEPENDENCY"
    );
    let (entry, receipt) = accepted(&state, "record", fixture()["record"].clone());
    let advanced = state.advance(&entry, &receipt).unwrap();
    assert_eq!(state.sequence, 5);
    assert_eq!(advanced.sequence, 6);
    let mut changed = receipt.clone();
    changed["previous_head_hash"] = json!(hash_bytes(&[2; 32]));
    assert!(state.advance(&entry, &changed).is_err());
    assert!(advanced.advance(&entry, &receipt).is_err());
}
#[test]
fn a_valid_self_signature_and_a_circular_peer_certificate_never_establish_membership() {
    let state = genesis();
    let mut device = fixture()["genesis"]["device_record"].clone();
    let key = SigningKey::from_bytes(&[42; 32]);
    let id = uuid::Uuid::new_v4().to_string();
    {
        let target = &mut device;
        target["device_id"] = json!(id);
        target["device_public_key"] = json!(URL_SAFE_NO_PAD.encode(key.verifying_key().as_bytes()));
        target["enrollment"]["device_id"] = target["device_id"].clone();
        target["enrollment"]["device_public_key"] = target["device_public_key"].clone();
        target["enrollment"]["certificate_id"] = json!(uuid::Uuid::new_v4().to_string());
        target["enrollment"]["signer_kind"] = json!("device");
        target["enrollment"]["signer_device_id"] = json!(id);
        target["enrollment"]["request_hash"] = json!(hash_bytes(&[7; 32]));
        recovery::sign(&mut target["enrollment"], &key, CTX_ENROLLMENT).unwrap();
        recovery::sign(target, &key, CTX_DEVICE_RECORD).unwrap();
    }
    let (entry, receipt) = accepted(&state, "device", device);
    assert_eq!(
        state.advance(&entry, &receipt).err().unwrap().0,
        "C_SCHEMA_VIOLATION"
    );
    assert_eq!(state.devices.len(), 1);
    assert_eq!(state.sequence, 5);
}

fn apply(state: Authority, kind: &str, object: Value) -> Authority {
    let (entry, receipt) = accepted(&state, kind, object);
    state.advance(&entry, &receipt).unwrap()
}
fn enrolled() -> Authority {
    let f = fixture();
    let mut state = apply(genesis(), "record", f["record"].clone());
    let body = &f["enrollment"]["body"];
    state = apply(state, "device", body["device_record"].clone());
    assert_eq!(state.complete_cut().unwrap_err().0, "C_MISSING_DEPENDENCY");
    for envelope in body["envelopes"].as_array().unwrap() {
        state = apply(state, "envelope", envelope.clone());
    }
    assert_eq!(state.complete_cut().unwrap_err().0, "C_MISSING_DEPENDENCY");
    state = apply(state, "admin", body["admin_operation"].clone());
    state.complete_cut().unwrap();
    state
}
fn roots() -> pallasync_core::crypto::RootKeys {
    pallasync_core::crypto::derive_root_keys(&format!("{} art", vec!["abandon"; 23].join(" ")), "")
        .unwrap()
}
#[test]
fn a_signed_admin_operation_must_name_the_exact_payload_cas_and_unique_id() {
    let f = fixture();
    let body = &f["enrollment"]["body"];
    let mut state = apply(genesis(), "record", f["record"].clone());
    state = apply(state, "device", body["device_record"].clone());
    for envelope in body["envelopes"].as_array().unwrap() {
        state = apply(state, "envelope", envelope.clone());
    }
    let key = roots();
    for field in [
        "expected_epoch_hash",
        "expected_parameters_hash",
        "target_hash",
    ] {
        let mut changed = body["admin_operation"].clone();
        changed[field] = json!(hash_bytes(&[19; 32]));
        recovery::sign(&mut changed, &key.admin_key, CTX_ADMIN_OP).unwrap();
        let (entry, receipt) = accepted(&state, "admin", changed);
        assert_eq!(
            state.advance(&entry, &receipt).err().unwrap().0,
            if field == "target_hash" {
                "C_MISSING_DEPENDENCY"
            } else {
                "C_SCHEMA_VIOLATION"
            }
        );
    }
    let mut wrong_action = body["admin_operation"].clone();
    wrong_action["action"] = json!("prune_log");
    recovery::sign(&mut wrong_action, &key.admin_key, CTX_ADMIN_OP).unwrap();
    let payload = json!({"device_record":body["device_record"],"envelopes":body["envelopes"]});
    assert_eq!(
        state
            .with_action_payload(&wrong_action, &payload)
            .err()
            .unwrap()
            .0,
        "C_SCHEMA_VIOLATION"
    );
    let valid = apply(state, "admin", body["admin_operation"].clone());
    let (entry, receipt) = accepted(&valid, "admin", body["admin_operation"].clone());
    assert_eq!(
        valid.advance(&entry, &receipt).err().unwrap().0,
        "C_SCHEMA_VIOLATION"
    );
}
fn revocation(state: &Authority) -> (Value, Vec<Value>, Value, Value) {
    use pallasync_core::crypto;
    let f = fixture();
    let root = roots();
    let survivor = &f["enrollment"]["body"]["device_record"];
    let previous = object_hash(&state.current_epoch().unwrap().record);
    let secret = [71; 32];
    let commitment = crypto::derive_epoch_keys(&root.chain_id_digest, 1, &secret)
        .unwrap()
        .epoch_commitment_b64u
        .clone();
    let device = serde_json::to_value(
        crypto::wrap_device_epoch_envelope(
            &uuid::Uuid::new_v4().to_string(),
            &state.pin.chain_id,
            0,
            1,
            Some(&previous),
            &commitment,
            survivor["device_id"].as_str().unwrap(),
            survivor["device_kex_public_key"].as_str().unwrap(),
            "admin",
            None,
            &root.admin_key,
            &secret,
        )
        .unwrap(),
    )
    .unwrap();
    let recovery_env = serde_json::to_value(
        crypto::wrap_recovery_epoch_envelope(
            &uuid::Uuid::new_v4().to_string(),
            &state.pin.chain_id,
            0,
            1,
            Some(&previous),
            &commitment,
            "admin",
            None,
            &root.admin_key,
            &root.recovery_kek,
            &secret,
            None,
        )
        .unwrap(),
    )
    .unwrap();
    let mut epoch = json!({"chain_id":state.pin.chain_id,"generation":0,"epoch":1,"previous_epoch_hash":previous,"epoch_commitment":commitment,"reason":"emergency","signer_kind":"admin","signer_device_id":null,"members":[survivor["device_id"]],"revoked_device_ids":[f["genesis"]["device_record"]["device_id"]],"recovery_envelope_hash":object_hash(&recovery_env)});
    recovery::sign(&mut epoch, &root.admin_key, CTX_EPOCH).unwrap();
    // Request order intentionally differs from the relay's device/recovery log order.
    let envelopes = vec![recovery_env, device];
    let payload = json!({"epoch":epoch,"envelopes":envelopes});
    let mut operation = json!({"operation_id":uuid::Uuid::new_v4().to_string(),"chain_id":state.pin.chain_id,"generation":0,"action":"revoke_device","expected_parameters_hash":object_hash(state.parameters.as_ref().unwrap()),"expected_epoch_hash":previous,"target_hash":object_hash(&payload),"created_at_ms":1791684000000u64});
    recovery::sign(&mut operation, &root.admin_key, CTX_ADMIN_OP).unwrap();
    (epoch, envelopes, operation, payload)
}
#[test]
fn revocation_needs_the_complete_exact_admin_payload_before_publishing_an_epoch() {
    let before = enrolled();
    let (epoch, envelopes, operation, payload) = revocation(&before);
    let mut staged = before.clone();
    for envelope in envelopes.iter().rev() {
        staged = apply(staged, "envelope", envelope.clone());
    }
    let (entry, receipt) = accepted(&staged, "epoch", epoch.clone());
    assert_eq!(
        staged.advance(&entry, &receipt).err().unwrap().0,
        "C_MISSING_DEPENDENCY"
    );
    staged = apply(staged, "admin", operation.clone());
    assert_eq!(staged.complete_cut().unwrap_err().0, "C_MISSING_DEPENDENCY");
    let (entry, receipt) = accepted(&staged, "epoch", epoch.clone());
    assert_eq!(
        staged.advance(&entry, &receipt).err().unwrap().0,
        "C_MISSING_DEPENDENCY"
    );
    assert_eq!(before.current_epoch().unwrap().record["epoch"], 0);
    let bound = staged.with_action_payload(&operation, &payload).unwrap();
    let after = bound.advance(&entry, &receipt).unwrap();
    after.complete_cut().unwrap();
    assert_eq!(after.current_epoch().unwrap().record["epoch"], 1);
    assert!(
        after.devices[fixture()["genesis"]["device_record"]["device_id"]
            .as_str()
            .unwrap()]
        .revoked_seq
        .is_some()
    );
    // An old, correctly signed record cannot be newly accepted above a rotation.
    assert_eq!(
        after
            .record_eligible(&fixture()["record"], after.sequence + 1)
            .err()
            .unwrap()
            .0,
        "C_ACCEPTANCE_UNPROVEN"
    );
    // Acceptance below that epoch boundary remains verifiable historical evidence.
    after.record_eligible(&fixture()["record"], 6).unwrap();
    let mut substituted = payload;
    substituted["envelopes"].as_array_mut().unwrap().pop();
    assert_eq!(
        staged
            .with_action_payload(&operation, &substituted)
            .err()
            .unwrap()
            .0,
        "C_SCHEMA_VIOLATION"
    );
}
