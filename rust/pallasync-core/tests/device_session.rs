use ed25519_dalek::SigningKey;
use pallasync_core::{
    crypto,
    device_session::{self, RecordContext},
    models::to_jcs,
};
use serde_json::json;

fn context() -> RecordContext {
    serde_json::from_value(json!({
    "chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","generation":0,
    "record_id":"11111111-1111-4111-8111-111111111111","device_id":"22222222-2222-4222-8222-222222222222",
    "epoch":0,"collection_tag":null
})).unwrap()
}

#[test]
fn compaction_signing_is_scoped_to_the_authenticated_device_key() {
    use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};

    let seed = [0x31; 32];
    let signing = SigningKey::from_bytes(&seed);
    let public_key = URL_SAFE_NO_PAD.encode(signing.verifying_key().as_bytes());
    let device_id = "22222222-2222-4222-8222-222222222222";
    let value = json!({
        "compaction_id":"55555555-5555-4555-8555-555555555555",
        "chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
        "generation":0,
        "device_id":device_id,
        "log_id":"33333333-3333-4333-8333-333333333333",
        "covered_through_seq":1,
        "covered_log_hash":URL_SAFE_NO_PAD.encode([0x41;32]),
        "epoch_hash":URL_SAFE_NO_PAD.encode([0x42;32]),
        "snapshot_parts":[{"record_id":"11111111-1111-4111-8111-111111111111","record_hash":URL_SAFE_NO_PAD.encode([0x43;32])}],
        "state_sha256":URL_SAFE_NO_PAD.encode([0x44;32]),
        "signature":""
    });
    let handle = device_session::begin(&seed, &[]).unwrap();
    let signed =
        device_session::sign_compaction(handle, value.clone(), device_id, &public_key).unwrap();
    pallasync_core::public_wire::verify(&signed, &public_key, crypto::CTX_COMPACTION).unwrap();
    assert_eq!(signed["device_id"], device_id);
    assert_eq!(
        device_session::sign_compaction(
            handle,
            value.clone(),
            "44444444-4444-4444-8444-444444444444",
            &public_key
        )
        .unwrap_err()
        .0,
        "C_DEVICE_KEY_MISMATCH"
    );
    assert_eq!(
        device_session::sign_compaction(
            handle,
            value.clone(),
            device_id,
            &URL_SAFE_NO_PAD.encode([0x55; 32])
        )
        .unwrap_err()
        .0,
        "C_DEVICE_KEY_MISMATCH"
    );
    device_session::end(handle);
    assert_eq!(
        device_session::sign_compaction(handle, value, device_id, &public_key)
            .unwrap_err()
            .0,
        "C_BOOTSTRAP_TRUST_REQUIRED"
    );
}

#[test]
fn device_token_session_has_no_record_key_or_administrative_override_and_uses_fresh_nonces() {
    use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
    let handle = device_session::begin(&[1; 32], &[]).unwrap();
    let binding = || {
        serde_json::from_value(json!({"aud":"https://relay.example.com",
        "chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","device_id":"22222222-2222-4222-8222-222222222222",
        "method":"POST","path":"/pallasync/v3/chains/ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc/records","query":""})).unwrap()
    };
    let first = device_session::token(handle, binding(), b"{}").unwrap();
    let second = device_session::token(handle, binding(), b"{}").unwrap();
    let first =
        pallasync_core::strict::jcs::parse(&URL_SAFE_NO_PAD.decode(first).unwrap()).unwrap();
    let second =
        pallasync_core::strict::jcs::parse(&URL_SAFE_NO_PAD.decode(second).unwrap()).unwrap();
    assert_eq!(first["signer_kind"], "device");
    assert_eq!(
        first["expires_at_ms"].as_u64().unwrap() - first["issued_at_ms"].as_u64().unwrap(),
        120_000
    );
    assert_ne!(first["nonce"], second["nonce"]);
    let corrected = device_session::token_at(handle, binding(), b"{}", 1791684000000).unwrap();
    let corrected =
        pallasync_core::strict::jcs::parse(&URL_SAFE_NO_PAD.decode(corrected).unwrap()).unwrap();
    assert_eq!(corrected["issued_at_ms"], 1791684000000u64);
    assert_eq!(corrected["expires_at_ms"], 1791684120000u64);
    assert_eq!(
        device_session::token_at(handle, binding(), b"{}", -1)
            .unwrap_err()
            .0,
        "C_SCHEMA_VIOLATION"
    );
    assert_eq!(
        device_session::token_at(
            handle,
            binding(),
            b"{}",
            pallasync_core::strict::jcs::MAX_INTEGER as i64
        )
        .unwrap_err()
        .0,
        "C_SCHEMA_VIOLATION"
    );
    let frame=device_session::prepare_frame(br#"{"collection":"palleria.search_history/3","device_seq":1,"prev_record_hash":null,"operations":[{"entity_id":"x","operation":"delete","lamport":1,"created_at_ms":1,"context":{},"body":{}}]}"#).unwrap();
    assert_eq!(
        device_session::create(handle, context(), &frame, &[3; 24])
            .unwrap_err()
            .0,
        "C_MISSING_DEPENDENCY"
    );
    device_session::end(handle);
    assert_eq!(
        device_session::token(handle, binding(), b"{}")
            .unwrap_err()
            .0,
        "C_BOOTSTRAP_TRUST_REQUIRED"
    );
}

#[test]
fn journaled_padding_and_nonce_reconstruct_exact_record_without_reusing_an_abandoned_sequence() {
    let inner = json!({"collection":"palleria.search_history/3","device_seq":1,"prev_record_hash":null,
        "operations":[{"entity_id":"test","operation":"upsert","lamport":1,"created_at_ms":1,"context":{},"body":{"query":"test"}}]});
    let frame = device_session::prepare_frame(&serde_json::to_vec(&inner).unwrap()).unwrap();
    let seed = [1; 32];
    let key = [2; 32];
    let nonce = [3; 24];
    let first_handle = device_session::begin(&seed, &key).unwrap();
    let first = device_session::create(first_handle, context(), &frame, &nonce).unwrap();
    device_session::end(first_handle);
    assert_eq!(
        device_session::create(first_handle, context(), &frame, &nonce)
            .unwrap_err()
            .0,
        "C_BOOTSTRAP_TRUST_REQUIRED"
    );
    let resumed = device_session::begin(&seed, &key).unwrap();
    let retry = device_session::create(resumed, context(), &frame, &nonce).unwrap();
    assert_eq!(to_jcs(&first).unwrap(), to_jcs(&retry).unwrap());
    let expected: pallasync_core::models::InnerRecord = serde_json::from_value(inner).unwrap();
    assert_eq!(crypto::decrypt_sync_record(&retry, &key).unwrap(), expected);
    let value = serde_json::to_value(&retry).unwrap();
    assert_eq!(
        device_session::decrypt_own(resumed, &value).unwrap(),
        expected
    );
    let other = device_session::begin(&[9; 32], &key).unwrap();
    assert_eq!(
        device_session::decrypt_own(other, &value).unwrap_err().0,
        "C_SIGNATURE_INVALID"
    );
    device_session::end(other);
    assert!(
        crypto::verify_sync_record(
            &retry,
            &base64::Engine::encode(
                &base64::engine::general_purpose::URL_SAFE_NO_PAD,
                SigningKey::from_bytes(&seed).verifying_key().as_bytes()
            )
        )
        .unwrap()
    );
    let mut invalid = frame.clone();
    invalid[..4].copy_from_slice(&u32::MAX.to_be_bytes());
    assert_eq!(
        device_session::create(resumed, context(), &invalid, &nonce)
            .unwrap_err()
            .0,
        "C_SCHEMA_VIOLATION"
    );
    device_session::end(resumed);
}
