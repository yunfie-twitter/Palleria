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

fn signed_device(key: &SigningKey) -> Value {
    let mut device = json!({
        "protocol_version": "2.1",
        "chain_id": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "device_id": "01890f3e-7c00-7000-8000-000000000002",
        "device_public_key": URL_SAFE_NO_PAD.encode(key.verifying_key().as_bytes()),
        "encrypted_device_name": URL_SAFE_NO_PAD.encode([8u8; 32]),
        "device_name_nonce": URL_SAFE_NO_PAD.encode([9u8; 24]),
        "status": "active",
        "created_at_ms": 1_700_000_000_000u64,
        "updated_at_ms": 1_700_000_000_000u64,
        "signature": "",
    });
    let mut unsigned = device.clone();
    unsigned.as_object_mut().unwrap().remove("signature");
    let mut input = b"PALLASYNC-DEVICE-RECORD-v2.1\0".to_vec();
    input.extend_from_slice(&models::to_jcs(&unsigned).unwrap());
    device["signature"] = json!(URL_SAFE_NO_PAD.encode(key.sign(&input).to_bytes()));
    device
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
fn verifies_signed_v21_device_registry_rows_before_read_capabilities() {
    let key = SigningKey::from_bytes(&[29u8; 32]);
    let device = signed_device(&key);
    let text = String::from_utf8(models::to_jcs(&device).unwrap()).unwrap();
    assert!(crypto::verify_device_record_v21_json(&text).unwrap());

    let mut altered = device.clone();
    altered["status"] = json!("revoked");
    let text = String::from_utf8(models::to_jcs(&altered).unwrap()).unwrap();
    assert!(!crypto::verify_device_record_v21_json(&text).unwrap());

    let mut unknown = device;
    unknown["extra"] = json!(true);
    let text = String::from_utf8(models::to_jcs(&unknown).unwrap()).unwrap();
    assert!(crypto::verify_device_record_v21_json(&text).is_err());
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

    let handle = pallasync_core::legacy_migration::begin(
        MNEMONIC,
        "",
        &salt,
        &keys.chain_id,
        &keys.admin_public_key,
    )
    .unwrap();
    let converted = pallasync_core::legacy_migration::convert_record(
        handle,
        &record,
        "01890f3e-7c00-7000-8000-000000000002",
        &public,
    )
    .unwrap();
    let candidate: Value = serde_json::from_slice(&converted).unwrap();
    assert_eq!(candidate[0]["collection"], "palleria.favorite_tag/3");
    assert_eq!(candidate[0]["body"]["tag"], "landscape");
    assert!(
        pallasync_core::legacy_migration::convert_record(
            handle,
            &record,
            "01890f3e-7c00-7000-8000-000000000003",
            &public,
        )
        .is_err()
    );
    pallasync_core::legacy_migration::end(handle);
    assert!(
        pallasync_core::legacy_migration::convert_record(
            handle,
            &record,
            "01890f3e-7c00-7000-8000-000000000002",
            &public,
        )
        .is_err()
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
    let mut tampered_signature = serde_json::from_str::<Value>(&record).unwrap();
    tampered_signature["signature"] = json!(URL_SAFE_NO_PAD.encode([0u8; 64]));
    assert_eq!(
        crypto::decrypt_sync_record_v21_json(&record_text(&tampered_signature), &public, &keys)
            .unwrap_err(),
        "C_SIGNATURE_INVALID"
    );
}

#[test]
fn legacy_get_capability_is_context_signed_read_only_and_query_bound() {
    let device = SigningKey::from_bytes(&[41u8; 32]);
    let seed = [41u8; 32];
    let chain = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    let device_id = "01890f3e-7c00-7000-8000-000000000002";
    let path = format!("/pallasync/v2/chains/{chain}/records");
    let query = "after_seq=0&limit=200";
    let encoded = crypto::create_legacy_v21_get_token(
        &seed,
        chain,
        device_id,
        &path,
        query,
        1_700_000_000_000,
    )
    .unwrap();
    let bytes = URL_SAFE_NO_PAD.decode(encoded).unwrap();
    let token: Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(token["v"], 1);
    assert_eq!(token["method"], "GET");
    assert_eq!(token["query"], query);
    assert_eq!(token["expires_at_ms"], 1_700_000_300_000u64);
    let signature = URL_SAFE_NO_PAD
        .decode(token["signature"].as_str().unwrap())
        .unwrap();
    let signature = ed25519_dalek::Signature::from_slice(&signature).unwrap();
    let mut unsigned = token.clone();
    unsigned.as_object_mut().unwrap().remove("signature");
    let mut signed = b"PALLASYNC-CAPABILITY-v2.1\0".to_vec();
    signed.extend_from_slice(&models::to_jcs(&unsigned).unwrap());
    device
        .verifying_key()
        .verify_strict(&signed, &signature)
        .unwrap();

    assert!(
        crypto::create_legacy_v21_get_token(
            &seed,
            chain,
            device_id,
            &path,
            "limit=200",
            1_700_000_000_000
        )
        .is_err()
    );
    assert!(
        crypto::create_legacy_v21_get_token(
            &seed,
            chain,
            device_id,
            &path,
            query,
            1_700_000_000_000
        )
        .is_ok()
    );
    assert!(
        crypto::create_legacy_v21_get_token(
            &seed,
            chain,
            device_id,
            "/pallasync/v2/chains/x/delete",
            "",
            1_700_000_000_000
        )
        .is_err()
    );
}
