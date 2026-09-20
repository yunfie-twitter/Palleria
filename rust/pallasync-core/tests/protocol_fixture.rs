use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use pallasync_core::{crypto, models};

const SEED_PHRASE: &str =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
const RECORD_ID: &str = "018f0c2a-7b9d-7000-8000-000000000001";
const DEVICE_ID: &str = "018f0c2a-7b9d-7000-8000-000000000002";
const CREATED_AT_MS: i64 = 1_700_000_000_123;
const PAYLOAD: &str = r#"{"entity_id":"landscape","value":"landscape"}"#;

#[test]
fn v2_1_key_derivation_encryption_signature_and_capability_tokens() {
    let keys = crypto::derive_keys_from_seed(SEED_PHRASE).expect("fixture seed is valid");
    let repeated_keys = crypto::derive_keys_from_seed(SEED_PHRASE).expect("fixture seed is valid");
    assert_eq!(keys.chain_id, repeated_keys.chain_id);
    assert_eq!(keys.chain_salt, repeated_keys.chain_salt);
    assert_eq!(keys.record_key, repeated_keys.record_key);
    assert_eq!(keys.epoch_key, repeated_keys.epoch_key);
    assert_eq!(keys.admin_key.to_bytes(), repeated_keys.admin_key.to_bytes());

    let record = crypto::create_sync_record_at(
        &keys.chain_id,
        RECORD_ID,
        "palleria.favorite_tag/2",
        "upsert",
        PAYLOAD.as_bytes(),
        DEVICE_ID,
        &keys.encryption_key,
        &keys.signing_key,
        1,
        CREATED_AT_MS,
    )
    .expect("fixture record can be created");

    assert_eq!(record.protocol_version, "2.1");
    assert_eq!(record.epoch, 0);
    assert_eq!(record.lamport, 1);
    assert!(!record.payload_nonce.is_empty());
    assert!(!record.encrypted_payload.is_empty());
    assert!(!record.signature.is_empty());

    let public_key = URL_SAFE_NO_PAD.encode(keys.signing_key.verifying_key().as_bytes());
    assert!(crypto::verify_sync_record(&record, &public_key).expect("fixture key is valid"));

    let mut relayed_record = serde_json::to_value(&record).expect("fixture serializes");
    relayed_record["relay_seq"] = serde_json::json!(42);
    assert!(
        crypto::verify_sync_record_json(&relayed_record.to_string(), &public_key)
            .expect("relay metadata is not part of the client signature")
    );

    let decrypted = crypto::decrypt_sync_record(&record, &keys.encryption_key)
        .expect("fixture decrypts");
    assert_eq!(decrypted, PAYLOAD.as_bytes());

    let mut tampered = record.clone();
    tampered.action = "delete".to_string();
    assert!(!crypto::verify_sync_record(&tampered, &public_key).expect("tampered record fails verification"));

    // Capability Token verification
    let token = crypto::create_capability_token(
        &keys.chain_id,
        DEVICE_ID,
        "POST",
        "/pallasync/v2/chains/test/records",
        "",
        b"{\"records\":[]}",
        &keys.signing_key,
        300_000,
    )
    .expect("capability token creates");
    assert!(!token.is_empty());

    let decoded_token_json = URL_SAFE_NO_PAD.decode(&token).expect("base64url decoded");
    let token_obj: models::CapabilityToken =
        serde_json::from_slice(&decoded_token_json).expect("valid token JSON");
    assert_eq!(token_obj.v, 1);
    assert_eq!(token_obj.device_id, DEVICE_ID);
    assert_eq!(token_obj.method, "POST");

    let token_canonical = models::capability_token_signing_bytes(&token_obj).expect("canonical JCS");
    let verifying_key = crypto::decode_verifying_key(&public_key).expect("valid pubkey");
    assert!(crypto::verify_with_context(
        &verifying_key,
        crypto::CTX_CAPABILITY,
        &token_canonical,
        &token_obj.signature,
    ).expect("valid capability token signature"));
}

#[test]
fn device_record_v2_1_self_signed_and_encrypted() {
    let keys = crypto::derive_keys_from_seed(SEED_PHRASE).expect("fixture seed is valid");
    let record = crypto::create_device_record_at(
        &keys.chain_id,
        DEVICE_ID,
        b"Palleria fixture device",
        &keys.encryption_key,
        &keys.signing_key,
        CREATED_AT_MS,
    )
    .expect("fixture device record can be created");

    assert_eq!(record.protocol_version, "2.1");
    assert_eq!(record.status, "active");
    assert!(!record.device_name_nonce.is_empty());
    assert!(crypto::verify_device_record(&record).expect("fixture record is well formed"));

    assert_eq!(
        crypto::decrypt_device_record_v21(&record, &keys.encryption_key)
            .expect("fixture device name decrypts"),
        b"Palleria fixture device"
    );

    let mut tampered = record;
    tampered.created_at_ms += 1;
    assert!(!crypto::verify_device_record(&tampered).expect("tampered record fails verification"));
}

