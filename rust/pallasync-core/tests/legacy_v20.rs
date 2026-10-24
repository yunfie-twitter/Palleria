use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use chacha20poly1305::{
    ChaCha20Poly1305, KeyInit, Nonce,
    aead::{Aead, Payload},
};
use ed25519_dalek::{Signer, SigningKey};
use hkdf::Hkdf;
use pallasync_core::{crypto, legacy_migration, models};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};

const MNEMONIC: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
const RECORD_ID: &str = "01890f3e-7c00-7000-8000-000000000001";
const DEVICE_ID: &str = "01890f3e-7c00-7000-8000-000000000002";

fn encrypted_record() -> (crypto::LegacyV20Keys, String, String) {
    let keys = crypto::derive_legacy_v20_keys(MNEMONIC, "").unwrap();
    let seed = crypto::derive_root_seed(MNEMONIC, "").unwrap();
    let hk = Hkdf::<Sha256>::new(Some(b"PallaSync-V2-Salt"), &seed);
    let mut encryption_key = [0u8; 32];
    let mut signing_seed = [0u8; 32];
    hk.expand(b"encryption_key", &mut encryption_key).unwrap();
    hk.expand(b"signing_key", &mut signing_seed).unwrap();
    let signing = SigningKey::from_bytes(&signing_seed);
    let payload = json!({
        "schema":"palleria.favorite_tag/1",
        "entity_id":"user_favorite_tags",
        "operation":"upsert",
        "context":{},
        "lamport":1,
        "created_at_ms":1_700_000_000_000_i64,
        "body":["one","two"]
    });
    let plaintext = models::to_jcs(&payload).unwrap();
    let nonce = crypto::derive_record_nonce_legacy(RECORD_ID);
    let ciphertext = ChaCha20Poly1305::new((&encryption_key).into())
        .encrypt(
            Nonce::from_slice(&nonce),
            Payload {
                msg: &plaintext,
                aad: crypto::LEGACY_SYNC_RECORD_AAD,
            },
        )
        .unwrap();
    let mut record = json!({
        "protocol_version":"2.0",
        "chain_id":keys.chain_id,
        "record_id":RECORD_ID,
        "collection_name":"palleria.favorite_tag/1",
        "action":"upsert",
        "encrypted_payload":URL_SAFE_NO_PAD.encode(ciphertext),
        "device_id":DEVICE_ID,
        "created_at_ms":1_700_000_000_000_i64,
        "signature":""
    });
    let mut unsigned = record.clone();
    unsigned.as_object_mut().unwrap().remove("signature");
    let digest = Sha256::digest(models::to_jcs(&unsigned).unwrap());
    record["signature"] = json!(URL_SAFE_NO_PAD.encode(signing.sign(&digest).to_bytes()));
    let text = String::from_utf8(models::to_jcs(&record).unwrap()).unwrap();
    (
        keys,
        text,
        URL_SAFE_NO_PAD.encode(signing.verifying_key().as_bytes()),
    )
}

#[test]
fn v20_deterministic_signature_aead_and_v1_conversion_use_the_specified_legacy_profile() {
    let (keys, record, _public) = encrypted_record();
    assert_eq!(keys.chain_id, "_iefvXsLUE_of7LKvJB_z-e6-OXGP5oG6SClqLIA8Ds");
    assert!(crypto::verify_sync_record_v20_json(&record, &keys).unwrap());
    let mut relayed: Value = serde_json::from_str(&record).unwrap();
    relayed["relay_seq"] = json!(17);
    relayed["server_sequence"] = json!(17);
    let relayed = String::from_utf8(models::to_jcs(&relayed).unwrap()).unwrap();
    assert!(crypto::verify_sync_record_v20_json(&relayed, &keys).unwrap());
    let plaintext = crypto::decrypt_sync_record_v20_json(&record, &keys).unwrap();
    assert_eq!(
        serde_json::from_slice::<Value>(&plaintext).unwrap()["schema"],
        "palleria.favorite_tag/1"
    );

    let handle = legacy_migration::begin_v20(MNEMONIC, "", &keys.chain_id).unwrap();
    let converted = legacy_migration::convert_record(handle, &record, DEVICE_ID, "").unwrap();
    let candidates: Value = serde_json::from_slice(&converted).unwrap();
    assert_eq!(candidates.as_array().unwrap().len(), 2);
    assert_eq!(candidates[0]["entity_id"], "one");
    assert_eq!(candidates[1]["entity_id"], "two");
    legacy_migration::end(handle);
}

#[test]
fn v20_identity_signature_and_fields_fail_closed() {
    let (keys, record, _) = encrypted_record();
    assert!(legacy_migration::begin_v20(MNEMONIC, "wrong", &keys.chain_id).is_err());
    let mut tampered: Value = serde_json::from_str(&record).unwrap();
    tampered["action"] = json!("delete");
    let tampered = String::from_utf8(models::to_jcs(&tampered).unwrap()).unwrap();
    assert!(!crypto::verify_sync_record_v20_json(&tampered, &keys).unwrap());

    let mut invalid_sequence: Value = serde_json::from_str(&record).unwrap();
    invalid_sequence["relay_seq"] = json!(-1);
    let invalid_sequence = String::from_utf8(models::to_jcs(&invalid_sequence).unwrap()).unwrap();
    assert!(crypto::verify_sync_record_v20_json(&invalid_sequence, &keys).is_err());
}
