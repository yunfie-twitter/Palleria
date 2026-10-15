use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use pallasync_core::{
    recovery::{self, RecoveryInput},
    root_session::{self, Operation},
    strict::{jcs, verify::object_hash},
};
use serde_json::json;
const MNEMONIC: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";

#[test]
fn c8_signed_manifest_share_wrapping_and_canonical_input() {
    let manifest = jcs::parse(include_bytes!("fixtures/recovery-manifest-c8.json")).unwrap();
    let record = jcs::parse(include_bytes!("fixtures/recovery-share-c8.json")).unwrap();
    let input = RecoveryInput::from_user(MNEMONIC, "").unwrap();
    let roots = input.roots().unwrap();
    assert_eq!(
        input.fingerprint().unwrap(),
        "ECxCaeLrAVjKLU9jdzKQqhbBPdA1mtP91FXNdPLpIr4"
    );
    recovery::validate_manifest(&manifest, &roots.admin_public_key).unwrap();
    assert_eq!(
        object_hash(&manifest),
        "AAK-KG_c1kPemS3aCSWBxcbKXMJQVNbs_52b_wHiJBg"
    );
    let rswk = std::array::from_fn(|i| i as u8 + 0x60);
    let piece = recovery::unwrap_piece(&record, &rswk).unwrap();
    assert_eq!(piece.index, 1);
    assert_eq!(piece.bytes, [0x20; 32]);
    assert!(recovery::unwrap_piece(&record, &[0; 32]).is_err());
    let mut altered = record.clone();
    altered["holder_instance_id"] = json!("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    assert!(recovery::unwrap_piece(&altered, &rswk).is_err());
    let input = RecoveryInput::from_user(MNEMONIC, "é").unwrap();
    assert_eq!(input.passphrase, "e\u{301}");
}
#[test]
fn root_handle_is_bound_to_named_operation_and_cannot_activate_undistributed_set() {
    let handle = root_session::begin(
        MNEMONIC.as_bytes(),
        b"",
        Operation::RecoveryCreate,
        None,
        None,
    )
    .unwrap();
    let identity = root_session::identity(handle).unwrap();
    assert_eq!(identity.as_object().unwrap().len(), 2);
    let holders = vec![
        json!({"holder_kind":"device","holder_device_id":"11111111-1111-4111-8111-111111111111","holder_instance_id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"}),
        json!({"holder_kind":"device","holder_device_id":"22222222-2222-4222-8222-222222222222","holder_instance_id":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"}),
        json!({"holder_kind":"offline","holder_device_id":null,"holder_instance_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}),
    ];
    assert!(
        root_session::create_set(handle, Operation::RecoveryRotate, 0, 2, &holders, 1).is_err()
    );
    let prepared =
        root_session::create_set(handle, Operation::RecoveryCreate, 0, 2, &holders, 1).unwrap();
    recovery::validate_manifest(
        &prepared["manifest"],
        identity["admin_public_key"].as_str().unwrap(),
    )
    .unwrap();
    assert_eq!(prepared["status_chain"][0]["state"], "prepared");
    assert_eq!(
        root_session::activate_set(handle, Operation::RecoveryCreate, 2)
            .err()
            .unwrap()
            .0,
        "C_RECOVERY_ROTATION_INCOMPLETE"
    );
    root_session::end(handle);
    assert!(root_session::identity(handle).is_err());
}

#[test]
fn delete_request_is_a_root_signed_cas_bound_admin_operation() {
    let handle =
        root_session::begin(MNEMONIC.as_bytes(), b"", Operation::Delete, None, None).unwrap();
    let identity = root_session::identity(handle).unwrap();
    let body: serde_json::Value = serde_json::from_str(
        &root_session::delete_request(
            handle,
            3,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            1_791_684_000_000,
        )
        .unwrap(),
    )
    .unwrap();
    let admin = &body["admin_operation"];
    assert_eq!(admin["action"], "delete_chain");
    assert_eq!(admin["generation"], 3);
    assert_eq!(
        admin["target_hash"],
        object_hash(&json!({
            "deletion_id": body["deletion_id"],
            "expected_parameters_hash": body["expected_parameters_hash"],
            "expected_epoch_hash": body["expected_epoch_hash"],
        }))
    );
    let key_bytes = URL_SAFE_NO_PAD
        .decode(identity["admin_public_key"].as_str().unwrap())
        .unwrap();
    let key = ed25519_dalek::VerifyingKey::from_bytes(&key_bytes.try_into().unwrap()).unwrap();
    let unsigned = pallasync_core::models::unsigned_record_jcs(admin).unwrap();
    assert!(
        pallasync_core::crypto::verify_with_context(
            &key,
            pallasync_core::crypto::CTX_ADMIN_OP,
            &unsigned,
            admin["signature"].as_str().unwrap(),
        )
        .unwrap()
    );
    root_session::end(handle);
}

#[test]
fn reseed_admin_operation_requires_reseed_session_and_strict_signed_inputs() {
    let wrong_operation =
        root_session::begin(MNEMONIC.as_bytes(), b"", Operation::Delete, None, None).unwrap();
    assert_eq!(
        root_session::reseed_admin_operation(
            wrong_operation,
            "{}",
            "{}",
            "{}",
            "{}",
            "[]",
            "{}",
            "[]",
            1,
        )
        .unwrap_err()
        .0,
        "C_RECOVERY_AUTH_REQUIRED"
    );
    root_session::end(wrong_operation);

    let reseed =
        root_session::begin(MNEMONIC.as_bytes(), b"", Operation::Reseed, None, None).unwrap();
    assert_eq!(
        root_session::reseed_admin_operation(
            reseed,
            r#"{"duplicate":1,"duplicate":2}"#,
            "{}",
            "{}",
            "{}",
            "[]",
            "{}",
            "[]",
            1,
        )
        .unwrap_err()
        .0,
        "C_SCHEMA_VIOLATION"
    );
    root_session::end(reseed);
}

#[test]
fn reseed_admin_operation_signs_the_exact_generation_and_cas_payload() {
    use pallasync_core::{crypto, recovery as recovery_signing};

    let bundle = crypto::create_genesis_bundle(
        MNEMONIC,
        "",
        "Reseed test",
        "os-keystore",
        "https://relay.example.com",
    )
    .unwrap();
    let genesis = jcs::parse(bundle.genesis_request_body_json.as_bytes()).unwrap();
    let current_parameters = genesis["parameters"].clone();
    let current_epoch = genesis["epoch"].clone();

    let mut parameters = current_parameters.clone();
    parameters["generation"] = json!(1);
    parameters["previous_parameters_hash"] = json!(object_hash(&current_parameters));
    let roots = recovery::RecoveryInput::from_user(MNEMONIC, "")
        .unwrap()
        .roots()
        .unwrap();
    recovery_signing::sign(
        &mut parameters,
        &roots.admin_key,
        pallasync_core::strict::verify::CTX_PARAMETERS,
    )
    .unwrap();

    let mut epoch = current_epoch.clone();
    epoch["generation"] = json!(1);
    epoch["epoch"] = json!(current_epoch["epoch"].as_u64().unwrap() + 1);
    epoch["reason"] = json!("reseed");
    epoch["previous_epoch_hash"] = json!(object_hash(&current_epoch));
    let mut device = genesis["device_record"].clone();
    device["generation"] = json!(1);
    device["enrollment"]["generation"] = json!(1);
    recovery_signing::sign(
        &mut device["enrollment"],
        &roots.admin_key,
        pallasync_core::strict::verify::CTX_ENROLLMENT,
    )
    .unwrap();
    let device_seed = base64::Engine::decode(
        &base64::engine::general_purpose::URL_SAFE_NO_PAD,
        &bundle.device_signing_key,
    )
    .unwrap();
    let device_signing = ed25519_dalek::SigningKey::from_bytes(&device_seed.try_into().unwrap());
    recovery_signing::sign(
        &mut device,
        &device_signing,
        pallasync_core::strict::verify::CTX_DEVICE_RECORD,
    )
    .unwrap();

    let previous_epoch_hash = object_hash(&current_epoch);
    let mut envelopes = genesis["envelopes"].as_array().unwrap().clone();
    for envelope in &mut envelopes {
        envelope["generation"] = json!(1);
        envelope["epoch"] = epoch["epoch"].clone();
        envelope["previous_epoch_hash"] = json!(previous_epoch_hash);
        envelope["epoch_commitment"] = epoch["epoch_commitment"].clone();
        envelope["signer_kind"] = json!("admin");
        envelope["signer_device_id"] = serde_json::Value::Null;
        recovery_signing::sign(
            envelope,
            &roots.admin_key,
            pallasync_core::strict::verify::CTX_ENVELOPE,
        )
        .unwrap();
    }
    let recovery_envelope = envelopes
        .iter()
        .find(|envelope| envelope["recipient_kind"] == "recovery")
        .unwrap();
    epoch["recovery_envelope_hash"] = json!(object_hash(recovery_envelope));
    recovery_signing::sign(
        &mut epoch,
        &roots.admin_key,
        pallasync_core::strict::verify::CTX_EPOCH,
    )
    .unwrap();

    let log_id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    let mut compaction = json!({
        "compaction_id":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        "chain_id":bundle.chain_id,
        "generation":1,
        "device_id":device["device_id"],
        "log_id":log_id,
        "covered_through_seq":0,
        "covered_log_hash":pallasync_core::attestations::data_zero(&bundle.chain_id, log_id).unwrap(),
        "epoch_hash":object_hash(&epoch),
        "snapshot_parts":[{"record_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","record_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}],
        "state_sha256":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "signature":""
    });
    recovery_signing::sign(
        &mut compaction,
        &device_signing,
        pallasync_core::strict::verify::CTX_COMPACTION,
    )
    .unwrap();
    let payload = json!({
        "parameters":parameters,
        "epoch":epoch,
        "envelopes":envelopes,
        "compaction":compaction,
        "device_records":[device]
    });
    pallasync_core::public_wire::validate(&current_parameters, "parameters").unwrap();
    pallasync_core::public_wire::validate(&current_epoch, "epoch").unwrap();
    pallasync_core::public_wire::validate(&payload["parameters"], "parameters").unwrap();
    pallasync_core::public_wire::validate(&payload["epoch"], "epoch").unwrap();
    pallasync_core::public_wire::validate(&payload["compaction"], "compaction").unwrap();
    pallasync_core::public_wire::validate(&payload["device_records"][0], "device").unwrap();
    pallasync_core::admin_action::schema("reseed", &payload).unwrap();
    let handle = root_session::begin(
        MNEMONIC.as_bytes(),
        b"",
        Operation::Reseed,
        Some(&bundle.chain_id),
        Some(&roots.admin_public_key),
    )
    .unwrap();
    let operation: serde_json::Value = serde_json::from_str(
        &root_session::reseed_admin_operation(
            handle,
            &current_parameters.to_string(),
            &current_epoch.to_string(),
            &payload["parameters"].to_string(),
            &payload["epoch"].to_string(),
            &payload["envelopes"].to_string(),
            &payload["compaction"].to_string(),
            &payload["device_records"].to_string(),
            1,
        )
        .unwrap(),
    )
    .unwrap();
    pallasync_core::admin_action::verify(&operation, &payload, &current_parameters, &current_epoch)
        .unwrap();
    assert_eq!(operation["target_hash"], object_hash(&payload));
    assert_eq!(
        operation["expected_parameters_hash"],
        object_hash(&current_parameters)
    );
    assert_eq!(
        operation["expected_epoch_hash"],
        object_hash(&current_epoch)
    );
    assert_eq!(
        root_session::reseed_admin_operation(
            handle,
            &current_parameters.to_string(),
            &current_epoch.to_string(),
            &payload["parameters"].to_string(),
            &payload["epoch"].to_string(),
            "[]",
            &payload["compaction"].to_string(),
            &payload["device_records"].to_string(),
            2,
        )
        .unwrap_err()
        .0,
        "C_SCHEMA_VIOLATION"
    );
    root_session::end(handle);
}
