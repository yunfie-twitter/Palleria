//! §7.4: validate before encryption and after authenticated decryption, including §7.11.
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use chacha20poly1305::{
    XChaCha20Poly1305, XNonce,
    aead::{Aead, KeyInit, Payload},
};
use ed25519_dalek::SigningKey;
use pallasync_core::{
    crypto,
    models::{self, DeviceMetaAAD, DeviceMetaPlaintext, DeviceRecord},
    strict::jcs,
};
use serde_json::json;

const PHRASE: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";

#[test]
fn metadata_boundaries_apply_to_local_construction_and_signed_received_ciphertext() {
    let bundle = crypto::create_genesis_bundle(
        PHRASE,
        "",
        "日本語 😀",
        "os-keystore",
        "https://relay.example.com",
    )
    .unwrap();
    let genesis = jcs::parse(bundle.genesis_request_body_json.as_bytes()).unwrap();
    let original: DeviceRecord = serde_json::from_value(genesis["device_record"].clone()).unwrap();
    let key: [u8; 32] = URL_SAFE_NO_PAD
        .decode(&bundle.device_meta_key)
        .unwrap()
        .try_into()
        .unwrap();
    let seed: [u8; 32] = URL_SAFE_NO_PAD
        .decode(&bundle.device_signing_key)
        .unwrap()
        .try_into()
        .unwrap();
    let signing = SigningKey::from_bytes(&seed);
    let ordinary = crypto::decrypt_device_meta(&original, &key).unwrap();
    assert_eq!(ordinary.name, "日本語 😀");
    let package =
        pallasync_core::recovery::portable(&json!({"type":"PALLASYNC-RECOVERY-PACKAGE-v3"}))
            .unwrap();
    for bad in [
        json!({"name":"", "key_protection":"os-keystore"}),
        json!({"name":"é".repeat(129), "key_protection":"os-keystore"}),
        json!({"name":"fixture", "key_protection":"unknown"}),
        json!({"name":"fixture", "key_protection":"os-keystore", "ext":{"org.palleria.fixture":{"type":"PALLASYNC-RECOVERY-SHARE-v3"}}}),
        json!({"name":"fixture", "key_protection":"os-keystore", "ext":{"org.palleria.fixture":{"file":package}}}),
    ] {
        let meta: DeviceMetaPlaintext = serde_json::from_value(bad.clone()).unwrap();
        assert!(
            crypto::create_device_record(
                &original.chain_id,
                original.generation,
                &original.device_id,
                &signing,
                &original.device_kex_public_key,
                &meta,
                original.meta_epoch,
                &key,
                original.enrollment.clone()
            )
            .is_err()
        );
        // Independently construct a correctly signed ciphertext; a valid AEAD/signature cannot excuse invalid plaintext.
        let aad = models::to_jcs(&DeviceMetaAAD {
            r#type: "PALLASYNC-DEVICE-META-v3",
            chain_id: &original.chain_id,
            generation: original.generation,
            device_id: &original.device_id,
            epoch: original.meta_epoch,
        })
        .unwrap();
        let framed = crypto::pad_and_frame(
            &jcs::canonicalize_json(&bad).unwrap(),
            crypto::DEVICE_META_BUCKETS,
            false,
        )
        .unwrap();
        let nonce = crypto::generate_nonce_24();
        let cipher = XChaCha20Poly1305::new((&key).into())
            .encrypt(
                XNonce::from_slice(&nonce),
                Payload {
                    msg: &framed,
                    aad: &aad,
                },
            )
            .unwrap();
        let mut malicious = original.clone();
        malicious.meta_nonce = URL_SAFE_NO_PAD.encode(nonce);
        malicious.encrypted_device_meta = URL_SAFE_NO_PAD.encode(cipher);
        crypto::sign_device_record(&mut malicious, &signing).unwrap();
        assert!(crypto::verify_device_record(&malicious).unwrap());
        assert_eq!(
            crypto::decrypt_device_meta(&malicious, &key).unwrap_err(),
            "C_SCHEMA_VIOLATION"
        );
    }
    let meta = DeviceMetaPlaintext {
        name: "fixture".into(),
        key_protection: "hardware".into(),
        ext: Some(json!({"org.palleria.fixture":{"text":"PSR3:debug"}})),
    };
    let record = crypto::create_device_record(
        &original.chain_id,
        original.generation,
        &original.device_id,
        &signing,
        &original.device_kex_public_key,
        &meta,
        original.meta_epoch,
        &key,
        original.enrollment.clone(),
    )
    .unwrap();
    assert_eq!(crypto::decrypt_device_meta(&record, &key).unwrap(), meta);
}
