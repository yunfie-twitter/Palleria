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
