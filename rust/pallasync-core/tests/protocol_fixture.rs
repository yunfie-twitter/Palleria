use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::{SigningKey, VerifyingKey};
use pallasync_core::crypto::{self};
use pallasync_core::models::{self, CapabilityToken, InnerRecord, Operation, RecordAAD, SyncRecord};

const KDF01_MNEMONIC: &str =
    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
const KDF01_EPOCH_SECRET_HEX: &str =
    "a0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebf";

#[test]
fn test_kdf_01_root_and_epoch_derivation() {
    let epoch_secret = hex::decode(KDF01_EPOCH_SECRET_HEX)
        .expect("valid hex")
        .try_into()
        .expect("32 bytes");

    let root_keys = crypto::derive_root_keys(KDF01_MNEMONIC, "").expect("valid mnemonic");

    assert_eq!(
        root_keys.chain_id,
        "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
    );
    assert_eq!(
        hex::encode(root_keys.chain_salt_bytes),
        "b31fa5eb6d786061fbf45e6a53d26fd5050a7f07b2c766398c4282d07356db14"
    );
    assert_eq!(
        hex::encode(root_keys.chain_id_digest),
        "383b4b5b8c36ee90fa2d2f48a4505f9af0728c4a0115d76fd85c7540920c2687"
    );
    assert_eq!(
        hex::encode(root_keys.admin_key.verifying_key().as_bytes()),
        "0e29ac96ebc027a701a35ec2fb21f831ac32f591fce5c075a1d7839dc0b2170d"
    );
    assert_eq!(
        hex::encode(root_keys.recovery_kek),
        "c9c01a7b0d75b4c31322a2d30a09cd8dcc785740059dbb99d243b70d48509eef"
    );

    let epoch_keys = crypto::derive_epoch_keys(&root_keys.chain_id_digest, 0, &epoch_secret)
        .expect("epoch keys derive");
    assert_eq!(
        hex::encode(epoch_keys.epoch_prk),
        "ac5c30f1cf5b9228f99318eead963b06b343c253a9b86555629c9752ef22a749"
    );
    assert_eq!(
        hex::encode(epoch_keys.record_key),
        "d96ff76d5c3ad5df7429153b52126e43a818c223f53fa3f52d7ce102026c0974"
    );
    assert_eq!(
        hex::encode(epoch_keys.device_meta_key),
        "b2ce14a6daf51febb34c0dfbcaa1264ff26395ef2c8561fdf8e4e2debd64d538"
    );
    assert_eq!(
        hex::encode(epoch_keys.collection_tag_key),
        "d3cf55aa08d1a91bf1f88f969e3759fc13238c374c2d2f5061dcbd0f762cf126"
    );
    assert_eq!(
        hex::encode(epoch_keys.epoch_commitment),
        "ddf4f8d731af10c1c98684337103bfe010bf84fb646e2cfa11aecafab986f493"
    );
}

#[test]
fn test_tag_01_collection_tag() {
    let epoch_secret = hex::decode(KDF01_EPOCH_SECRET_HEX)
        .unwrap()
        .try_into()
        .unwrap();
    let root_keys = crypto::derive_root_keys(KDF01_MNEMONIC, "").unwrap();
    let epoch_keys = crypto::derive_epoch_keys(&root_keys.chain_id_digest, 0, &epoch_secret).unwrap();

    let tag = crypto::compute_collection_tag(
        &epoch_keys.collection_tag_key,
        "palleria.search_history/3",
    );
    assert_eq!(tag, "ug37uPKZb2rNd2W3emGy3A");
}

#[test]
fn test_aad_01_and_frame_01() {
    let epoch_secret = hex::decode(KDF01_EPOCH_SECRET_HEX)
        .unwrap()
        .try_into()
        .unwrap();
    let root_keys = crypto::derive_root_keys(KDF01_MNEMONIC, "").unwrap();
    let epoch_keys = crypto::derive_epoch_keys(&root_keys.chain_id_digest, 0, &epoch_secret).unwrap();

    let aad_obj = RecordAAD {
        r#type: "PALLASYNC-AAD-v3",
        version: "3.0",
        chain_id: "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
        generation: 0,
        record_id: "22222222-2222-4222-8222-222222222222",
        device_id: "11111111-1111-4111-8111-111111111111",
        epoch: 0,
        collection_tag: None,
    };
    let aad = models::to_jcs(&aad_obj).unwrap();
    let aad_hash = crypto::sha256_hash(&aad);
    assert_eq!(
        hex::encode(aad_hash),
        "82442b8d2cf526ceabd7665e558015d4322fbb509861b3224869b50c7c03081b"
    );

    let inner_record = InnerRecord {
        collection: "palleria.search_history/3".to_string(),
        device_seq: 1,
        prev_record_hash: None,
        operations: vec![Operation {
            entity_id: "alpha".to_string(),
            operation: "upsert".to_string(),
            lamport: 1,
            created_at_ms: 0,
            context: serde_json::json!({}),
            body: serde_json::json!({"query": "alpha"}),
            ext: None,
        }],
        ext: None,
    };

    let inner_jcs = models::to_jcs(&inner_record).unwrap();
    assert_eq!(inner_jcs.len(), 207);

    let framed = crypto::pad_and_frame(&inner_jcs, crypto::SIZE_BUCKETS, true).unwrap();
    assert_eq!(framed.len(), 1024);
    assert_eq!(&framed[0..4], &[0x00, 0x00, 0x00, 0xcf]);
    let frame_hash = crypto::sha256_hash(&framed);
    assert_eq!(
        hex::encode(frame_hash),
        "e55a4a69e1f6ae0717a1a5623f9363c680d98261de1b38a371e6329706463de5"
    );

    // Encrypt with test nonce
    let nonce_bytes: [u8; 24] = hex::decode("000102030405060708090a0b0c0d0e0f1011121314151617")
        .unwrap()
        .try_into()
        .unwrap();
    use chacha20poly1305::{KeyInit, XChaCha20Poly1305, XNonce, aead::{Aead, Payload}};
    let cipher = XChaCha20Poly1305::new((&epoch_keys.record_key).into());
    let ciphertext = cipher
        .encrypt(XNonce::from_slice(&nonce_bytes), Payload { msg: &framed, aad: &aad })
        .unwrap();

    assert_eq!(ciphertext.len(), 1040);
    let ct_hash = crypto::sha256_hash(&ciphertext);
    assert_eq!(
        hex::encode(ct_hash),
        "39880e797ab2ca7c56b0f1be944daf5beddf5e3d64ae00c5351acaf846af94c7"
    );
    assert_eq!(
        hex::encode(&ciphertext[1024..]),
        "7913b8acf060a5a679da620187a17848"
    );
}

#[test]
fn test_sig_01_signature_verification() {
    let device_seed: [u8; 32] = hex::decode("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        .unwrap()
        .try_into()
        .unwrap();
    let signing_key = SigningKey::from_bytes(&device_seed);
    let pub_key_bytes = signing_key.verifying_key().to_bytes();
    assert_eq!(
        hex::encode(pub_key_bytes),
        "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8"
    );

    let sig_01_json = r#"{"chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","collection_tag":null,"device_id":"11111111-1111-4111-8111-111111111111","encrypted_payload":"I_Hq5dpQ299TQunj1YuERyJWM-ZtUmbDwletmLkKLFtYRDYD1ooCVJo4C_HyEajIS0iij4BbTCq6_q04qgrdYIJ6theaNYYdEH3WF8Duojr76G9JOPb0Xe-gLZ9IRUR7med8tR2HX9ywaGiwFgZkvJgcYbFA-5nLB8sc8hkuIFVBrbMwopqgqeS3uaTwqo1be5jhUFU8IcyF_rRAmmFYx2rHq99yEmFTECQejPwqClkRx1GYFQ3DhFHTjKkwKmlF2XLCo-HklmsbhcXX60pqoQ-4yG2vcyMVWYL7monM_fZXIzA-5XNuZIfiMhojfaFWkOniWlbHdjh1Y-4prXpmZeF1fybAeahHZekP655spEX-vQlwoF23MCDfYkfWWJFPx9wr0MCJoXUqdJj6yL44BgQW3GcdjLVj4EaQF_ZKQqdMA1r0oGWD-5zSslehEGmsJ1jTzbu12LYYETJ-Yvt0rVB0VGDRUVCUbRD_Us8mpZ32XD9yvKqW5mlwqmNfDx4nAP1qWCl82PQvd43aX5zdbRY60WkJ9L7mrDIJ8Yt7nOFTCdh-SMUruUgPF7KiEBIGsoWxlf3NbGz0BymuEOpYmK1qIRgTcB2jFqkaPScUXTm3PzTkQVgIqxksd0uZ_B48rq7wbdgG2b_ZANtO6UfthN70reFkiHurLuCevk4F7rx45O8Eqyopqa5WKa6v28NtQPmOOIFBRrSA6j7m6a23rab10POY8EG44AAghHO9cIHETlq4S9rcnpPJZAhwy5l6TK8MY7ahFNRN3bK8k_TTzIHd-o6ifJJaQ9xMXATITvrYgp978X-XDqzBbH3N17MvkwhqODCDM2Si8WEIRUsQ-M-H17dGcm1BwR28NkA9WqSNOjcLmSku_7G1olHLqrlQWeThxKBvMP3OTREz817ZJMFURMx88TTXdWpJK_2TblZ4NF3ezS3tI3WUB5fYwB_xNXevTFptwcAtAIQatvkCOKMYjyd1jXzexl8VlJg1syyB4eWKJ_pJthe1xysW1Hs6pOZIMTxd4uu0X4wW2zG5DrSNVdqiJ7Nus-J7r8RwN8EeQBD91_9G7uH-pwX88yGBsl0DoZ5HXDRPCOgX-QmwI0gEyzj_ziaXACY-QKRDiEJyr0Rdp_pLG9o5g6SJxODm0w2eZi2schKLUZe_AuQkoa9sImbLExl3qReCi9_MHhUyee-0Nyk1OuWTMO9Ihv5WI7kGlfnjxtEIITniV7vnTAR5Y3bsyOivfvLd9gDYJZi6DunDkSBy9w-JXRzNbZZ13JHJ66R0C9_KwXrxvKPkbj_Sh7QSgooxoIvfkXeLSHeMSrR9vXulBTlRP08Saaop1TaILv8REz4wXV-_xQpUA3kTuKzwYKWmedpiAYeheEg","epoch":0,"generation":0,"payload_nonce":"AAECAwQFBgcICQoLDA0ODxAREhMUFRYX","record_id":"22222222-2222-4222-8222-222222222222","signature":"8T6VtLueZ5ASqyf0a0GRHTJTUBHP-3L6Vn-uy2zKkG5JnYg8Uvi6XkCa3lrdErVS01OrsuQL5cROf-hDXzytCQ","version":"3.0"}"#;

    let record: SyncRecord = serde_json::from_str(sig_01_json).expect("valid SyncRecord JSON");
    let pub_key_b64u = URL_SAFE_NO_PAD.encode(pub_key_bytes);
    assert!(crypto::verify_sync_record(&record, &pub_key_b64u).expect("valid signature"));

    // Check with relay metadata added
    let mut relayed = serde_json::to_value(&record).unwrap();
    relayed["relay_seq"] = serde_json::json!(123);
    assert!(crypto::verify_sync_record_json(&relayed.to_string(), &pub_key_b64u).unwrap());

    // Tampering fails
    let mut tampered = record.clone();
    tampered.epoch = 1;
    assert!(!crypto::verify_sync_record(&tampered, &pub_key_b64u).unwrap());
}

#[test]
fn test_capability_token_creation_and_canonical_query() {
    let (signing_key, _) = crypto::generate_device_keys();
    let pub_key_b64u = URL_SAFE_NO_PAD.encode(signing_key.verifying_key().as_bytes());

    let token_b64u = crypto::create_capability_token(
        "https://api.yunfi.f5.si",
        "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
        "device",
        Some("11111111-1111-4111-8111-111111111111"),
        "POST",
        "/pallasync/v3/chains/ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc/records",
        "limit=200&after_seq=0",
        b"{}",
        &signing_key,
        120_000,
    )
    .expect("capability token created");

    let token_json_bytes = URL_SAFE_NO_PAD.decode(&token_b64u).expect("decoded base64url");
    let token: CapabilityToken = serde_json::from_slice(&token_json_bytes).expect("valid token json");

    assert_eq!(token.v, 3);
    assert_eq!(token.aud, "https://api.yunfi.f5.si");
    assert_eq!(token.query, "after_seq=0&limit=200");
    assert_eq!(token.signer_kind, "device");
    assert_eq!(
        token.device_id.as_deref(),
        Some("11111111-1111-4111-8111-111111111111")
    );

    assert!(crypto::verify_capability_token(&token, &pub_key_b64u).expect("token verified"));
}

#[test]
fn test_sync_record_full_lifecycle() {
    let root_keys = crypto::derive_root_keys(KDF01_MNEMONIC, "").unwrap();
    let epoch_secret = crypto::generate_epoch_secret();
    let epoch_keys = crypto::derive_epoch_keys(&root_keys.chain_id_digest, 0, &epoch_secret).unwrap();

    let (device_signing_key, _) = crypto::generate_device_keys();
    let device_pub_b64u = URL_SAFE_NO_PAD.encode(device_signing_key.verifying_key().as_bytes());

    let inner = InnerRecord {
        collection: "palleria.favorite_tag/3".to_string(),
        device_seq: 1,
        prev_record_hash: None,
        operations: vec![Operation {
            entity_id: "pixiv".to_string(),
            operation: "upsert".to_string(),
            lamport: 1,
            created_at_ms: 1791684000000,
            context: serde_json::json!({"order_key": "1"}),
            body: serde_json::json!({"tag": "pixiv"}),
            ext: None,
        }],
        ext: None,
    };

    let record = crypto::create_sync_record(
        &root_keys.chain_id,
        0,
        "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        0,
        None,
        &inner,
        &epoch_keys.record_key,
        &device_signing_key,
        false,
    )
    .expect("created sync record");

    assert!(crypto::verify_sync_record(&record, &device_pub_b64u).unwrap());

    let decrypted = crypto::decrypt_sync_record(&record, &epoch_keys.record_key).unwrap();
    assert_eq!(decrypted, inner);
}

#[test]
fn test_hpke_01_and_recovery_01_envelopes() {
    let epoch_secret: [u8; 32] = hex::decode(KDF01_EPOCH_SECRET_HEX)
        .unwrap()
        .try_into()
        .unwrap();
    let root_keys = crypto::derive_root_keys(KDF01_MNEMONIC, "").unwrap();

    // HPKE-01 test vector envelope
    let hpke_01_json = r#"{"chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","ciphertext":"9vikn8cAvrlCPqmNU3R4TjkCs_Fw9-lDDRrRRwXL5T-uK_qFcYo9nuEjxwR9Th80","enc":"eaYx7t4b-cmPEgMs3q3Q56B5OY_HhriMyEbsia-FpRo","envelope_id":"44444444-4444-4444-8444-444444444444","epoch":0,"epoch_commitment":"3fT41zGvEMHJhoQzcQO_4BC_hPtkbiz6Ea7K-rmG9JM","generation":0,"nonce":null,"previous_epoch_hash":null,"recipient_device_id":"11111111-1111-4111-8111-111111111111","recipient_key_hash":"oCiYEAMutBcnTuvEo45ombk1Mh1TVU4y7GMi8Poeck4","recipient_kind":"device","signature":"6E0YidG1-Z2oIMzFci0JyVFjX01-BwxqXxg8SSDWEQGUrJOaO0nlSR6_u4rQJEN_4hQ1iDJ0VsrJItiun3O4AA","signer_device_id":"11111111-1111-4111-8111-111111111111","signer_kind":"device"}"#;

    let device_envelope: models::EpochKeyEnvelope = serde_json::from_str(hpke_01_json).unwrap();
    let recipient_x25519_sk: [u8; 32] = hex::decode("202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f")
        .unwrap()
        .try_into()
        .unwrap();

    let unwrapped_device_secret = crypto::unwrap_device_epoch_envelope(
        &device_envelope,
        &recipient_x25519_sk,
        &root_keys.chain_id_digest,
    )
    .expect("HPKE unwrap succeeds");
    assert_eq!(unwrapped_device_secret, epoch_secret);

    // RECOVERY-01 test vector envelope
    let recovery_01_json = r#"{"chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","ciphertext":"rrR4otAXCIwwqYkaeqeXmkRnNMtLOExnZZrNxkcq58nN4rN42R31yvdUJXf2O_r5","enc":null,"envelope_id":"55555555-5555-4555-8555-555555555555","epoch":0,"epoch_commitment":"3fT41zGvEMHJhoQzcQO_4BC_hPtkbiz6Ea7K-rmG9JM","generation":0,"nonce":"GBkaGxwdHh8gISIjJCUmJygpKissLS4v","previous_epoch_hash":null,"recipient_device_id":null,"recipient_key_hash":null,"recipient_kind":"recovery","signature":"OenLBOTZTIGhUxg7iqFrPnm0gawvCOt7K8-Rq1k8DQc6JIuRzJCpnQ720LoIRYTvML-pi6lqLAv2WlmqHDgYDQ","signer_device_id":"11111111-1111-4111-8111-111111111111","signer_kind":"device"}"#;

    let recovery_envelope: models::EpochKeyEnvelope = serde_json::from_str(recovery_01_json).unwrap();
    let unwrapped_recovery_secret = crypto::unwrap_recovery_epoch_envelope(
        &recovery_envelope,
        &root_keys.recovery_kek,
        &root_keys.chain_id_digest,
    )
    .expect("Recovery unwrap succeeds");
    assert_eq!(unwrapped_recovery_secret, epoch_secret);
}

#[test]
fn rejects_weak_ed25519_public_keys() {
    let mut identity = [0_u8; 32];
    identity[0] = 1;
    let public_key = VerifyingKey::from_bytes(&identity).expect("encoded identity point");
    assert!(public_key.is_weak());
    let mut signature = [0_u8; 64];
    signature[..32].copy_from_slice(&identity);

    assert!(
        !crypto::verify_with_context(
            &public_key,
            crypto::CTX_SYNC_RECORD,
            b"{}",
            &URL_SAFE_NO_PAD.encode(signature),
        )
        .expect("signature encoding is well formed")
    );
}

#[test]
fn test_genesis_bundle_creation() {
    let bundle = crypto::create_genesis_bundle(
        KDF01_MNEMONIC,
        "",
        "Pixel 9 Pro",
        "os-keystore",
        "https://api.yunfi.f5.si",
    )
    .expect("Genesis bundle creation succeeded");

    assert_eq!(bundle.chain_id, "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc");
    assert_eq!(bundle.epoch, 0);
    assert!(!bundle.device_id.is_empty());
    assert!(!bundle.genesis_request_body_json.is_empty());
    assert!(!bundle.admin_capability_token.is_empty());
}

