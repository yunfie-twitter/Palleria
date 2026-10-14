use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use chacha20poly1305::{
    KeyInit, XChaCha20Poly1305, XNonce,
    aead::{Aead, Payload},
};
use ed25519_dalek::{Signer, SigningKey};
use pallasync_core::{crypto, models};
use serde_json::{Value, json};

const MNEMONIC: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";

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

fn encrypted_record(keys: &crypto::LegacyV21Keys, device_signing_key: &SigningKey) -> Value {
    let device_id = "01890f3e-7c00-7000-8000-000000000002";
    let record_id = "01890f3e-7c00-7000-8000-000000000001";
    let nonce = [8u8; 24];
    let plaintext =
        br#"{"body":{"tag":"landscape"},"context":{},"created_at_ms":1700000000000,"entity_id":"landscape","lamport":1,"operation":"upsert","schema":"palleria.favorite_tag/2"}"#;
    let aad_object = models::legacy::RecordAADV21 {
        protocol_version: "2.1",
        chain_id: &keys.chain_id,
        record_id,
        epoch: 0,
        collection_name: "palleria.favorite_tag/2",
        action: "upsert",
        device_id,
        lamport: 1,
        created_at_ms: 1_700_000_000_000,
    };
    let aad = models::to_jcs(&aad_object).unwrap();
    let record_key = crypto::legacy_v21_record_key(keys, 0).unwrap();
    let ciphertext = XChaCha20Poly1305::new((&*record_key).into())
        .encrypt(
            XNonce::from_slice(&nonce),
            Payload {
                msg: plaintext,
                aad: &aad,
            },
        )
        .unwrap();
    let record = json!({
        "protocol_version": "2.1",
        "chain_id": keys.chain_id,
        "record_id": record_id,
        "epoch": 0,
        "collection_name": "palleria.favorite_tag/2",
        "action": "upsert",
        "encrypted_payload": URL_SAFE_NO_PAD.encode(ciphertext),
        "payload_nonce": URL_SAFE_NO_PAD.encode(nonce),
        "device_id": device_id,
        "lamport": 1,
        "created_at_ms": 1_700_000_000_000u64,
        "signature": "",
    });
    let mut record = record;
    let mut unsigned = record.clone();
    unsigned.as_object_mut().unwrap().remove("signature");
    let mut signed = b"PALLASYNC-SYNC-RECORD-v2.1\0".to_vec();
    signed.extend_from_slice(&models::to_jcs(&unsigned).unwrap());
    record["signature"] =
        json!(URL_SAFE_NO_PAD.encode(device_signing_key.sign(&signed).to_bytes()));
    record
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

#[test]
fn derives_legacy_identity_and_decrypts_only_authenticated_v21_aad() {
    let salt = [4u8; 32];
    let keys = crypto::derive_legacy_v21_keys(MNEMONIC, "", &salt).unwrap();
    assert_eq!(keys.chain_id, "g76yyPCKGrNpNrfPgHGqdNsMmTLCZMg1lawtIPENYcM");
    assert_eq!(
        keys.admin_public_key,
        "sidFYvDxRt_JRoVJDO4FSBEqpI6Ty4e08S0wKUGBu0I"
    );
    assert!(
        crypto::legacy_v21_identity_matches(
            &keys,
            &keys.chain_id,
            &URL_SAFE_NO_PAD.encode(salt),
            &keys.admin_public_key,
        )
        .unwrap()
    );
    assert!(
        !crypto::legacy_v21_identity_matches(
            &keys,
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            &URL_SAFE_NO_PAD.encode(salt),
            &keys.admin_public_key,
        )
        .unwrap()
    );

    let device = SigningKey::from_bytes(&[31u8; 32]);
    let record = encrypted_record(&keys, &device);
    let record = record_text(&record);
    let public = URL_SAFE_NO_PAD.encode(device.verifying_key().as_bytes());
    let plaintext = crypto::decrypt_sync_record_v21_json(&record, &public, &keys).unwrap();
    assert_eq!(
        plaintext.as_slice(),
        br#"{"body":{"tag":"landscape"},"context":{},"created_at_ms":1700000000000,"entity_id":"landscape","lamport":1,"operation":"upsert","schema":"palleria.favorite_tag/2"}"#
    );

    let mut tampered_aad = serde_json::from_str::<Value>(&record).unwrap();
    tampered_aad["action"] = json!("delete");
    let mut unsigned = tampered_aad.clone();
    unsigned.as_object_mut().unwrap().remove("signature");
    let mut signed = b"PALLASYNC-SYNC-RECORD-v2.1\0".to_vec();
    signed.extend_from_slice(&models::to_jcs(&unsigned).unwrap());
    tampered_aad["signature"] = json!(URL_SAFE_NO_PAD.encode(device.sign(&signed).to_bytes()));
    assert_eq!(
        crypto::decrypt_sync_record_v21_json(&record_text(&tampered_aad), &public, &keys)
            .unwrap_err(),
        "C_AEAD_FAILURE"
    );
}
