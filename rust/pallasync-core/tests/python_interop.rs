//! Public artifacts produced by the independent Python client and accepted by an isolated relay.
use pallasync_core::{attestations, crypto, models, strict::jcs};
use serde_json::Value;

#[test]
fn python_genesis_receipts_and_encrypted_objects_verify_in_palleria_core() {
    let fixture: Value =
        jcs::parse(include_bytes!("fixtures/python-genesis-acceptance.json")).unwrap();
    let checkpoint = attestations::verify_genesis_acceptance(
        &fixture["created"],
        &fixture["genesis"],
        &fixture["identity"],
        fixture["origin"].as_str().unwrap(),
        fixture["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    assert_eq!(checkpoint["head_seq"], 5);
    assert_eq!(checkpoint["transparency_index"], 0); // No fabricated inclusion claim.
    let phrase = [vec!["abandon"; 23], vec!["art"]].concat().join(" ");
    let roots = crypto::derive_root_keys(&phrase, "").unwrap();
    assert_eq!(fixture["identity"]["chain_id"], roots.chain_id);
    assert_eq!(
        fixture["identity"]["admin_public_key"],
        roots.admin_public_key
    );
    let recovery = fixture["genesis"]["envelopes"]
        .as_array()
        .unwrap()
        .iter()
        .find(|v| v["recipient_kind"] == "recovery")
        .unwrap();
    let recovery: models::EpochKeyEnvelope = serde_json::from_value(recovery.clone()).unwrap();
    let secret = crypto::unwrap_recovery_epoch_envelope(
        &recovery,
        &roots.recovery_kek,
        &roots.chain_id_digest,
    )
    .unwrap();
    let keys = crypto::derive_epoch_keys(&roots.chain_id_digest, 0, &secret).unwrap();
    let device: models::DeviceRecord =
        serde_json::from_value(fixture["genesis"]["device_record"].clone()).unwrap();
    let meta = crypto::decrypt_device_meta(&device, &keys.device_meta_key).unwrap();
    assert_eq!(meta.name, "Independent Python test client");
    let record: models::SyncRecord = serde_json::from_value(fixture["record"].clone()).unwrap();
    assert!(crypto::verify_sync_record(&record, &device.device_public_key).unwrap());
    let inner = crypto::decrypt_sync_record(&record, &keys.record_key).unwrap();
    assert_eq!(inner.device_seq, 1);
    assert_eq!(inner.operations[0].body["query"], "猫");
    let mut missing = fixture["created"].clone();
    missing["upload_receipts"].as_array_mut().unwrap().pop();
    assert!(
        attestations::verify_genesis_acceptance(
            &missing,
            &fixture["genesis"],
            &fixture["identity"],
            fixture["origin"].as_str().unwrap(),
            fixture["relay_public_key"].as_str().unwrap()
        )
        .is_err()
    );
    let mut wrong = fixture["created"].clone();
    wrong["head_hash"] = serde_json::json!(pallasync_core::strict::verify::hash_bytes(&[1; 32]));
    assert!(
        attestations::verify_genesis_acceptance(
            &wrong,
            &fixture["genesis"],
            &fixture["identity"],
            fixture["origin"].as_str().unwrap(),
            fixture["relay_public_key"].as_str().unwrap()
        )
        .is_err()
    );
}
