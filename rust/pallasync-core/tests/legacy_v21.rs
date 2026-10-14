use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::{Signer, SigningKey};
use pallasync_core::{crypto, models};
use serde_json::{Value, json};

fn signed_record(key: &SigningKey) -> Value {
    let mut record = json!({
        "protocol_version": "2.1",
        "chain_id": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "record_id": "01890f3e-7c00-7000-8000-000000000001",
        "epoch": 0,
        "collection_name": "palleria.favorite_tag/2",
        "action": "upsert",
        "encrypted_payload": URL_SAFE_NO_PAD.encode([7u8; 32]),
        "payload_nonce": URL_SAFE_NO_PAD.encode([8u8; 24]),
        "device_id": "01890f3e-7c00-7000-8000-000000000002",
        "lamport": 1,
        "created_at_ms": 1_700_000_000_000u64,
        "signature": "",
    });
    let mut unsigned = record.clone();
    unsigned.as_object_mut().unwrap().remove("signature");
    let canonical = models::to_jcs(&unsigned).unwrap();
    let mut signed = b"PALLASYNC-SYNC-RECORD-v2.1\0".to_vec();
    signed.extend_from_slice(&canonical);
    record["signature"] = json!(URL_SAFE_NO_PAD.encode(key.sign(&signed).to_bytes()));
    record
}

fn record_text(record: &Value) -> String {
    String::from_utf8(models::to_jcs(record).unwrap()).unwrap()
}

#[test]
fn verifies_v21_record_and_excludes_relay_sequence_metadata() {
    let key = SigningKey::from_bytes(&[19u8; 32]);
    let public = URL_SAFE_NO_PAD.encode(key.verifying_key().as_bytes());
    let record = signed_record(&key);
    let text = record_text(&record);
    assert!(crypto::verify_sync_record_v21_json(&text, &public).unwrap());

    let mut relayed = record;
    relayed["relay_seq"] = json!(87);
    relayed["server_sequence"] = json!(87);
    let text = record_text(&relayed);
    assert!(crypto::verify_sync_record_v21_json(&text, &public).unwrap());
}

#[test]
fn rejects_tampered_or_non_v21_records_and_unknown_members() {
    let key = SigningKey::from_bytes(&[23u8; 32]);
    let public = URL_SAFE_NO_PAD.encode(key.verifying_key().as_bytes());
    let mut record = signed_record(&key);
    record["lamport"] = json!(2);
    assert!(!crypto::verify_sync_record_v21_json(&record_text(&record), &public).unwrap());

    let mut record = signed_record(&key);
    record["unsupported"] = json!(true);
    assert!(crypto::verify_sync_record_v21_json(&record_text(&record), &public).is_err());

    let mut record = signed_record(&key);
    record["protocol_version"] = json!("3.0");
    assert!(crypto::verify_sync_record_v21_json(&record_text(&record), &public).is_err());
}
