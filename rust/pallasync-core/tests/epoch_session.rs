use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::SigningKey;
use pallasync_core::{
    crypto,
    epoch_session::{self, Context},
    models::{InnerRecord, Operation},
    strict::verify::decode,
};
use serde_json::json;

fn context(profile: &str) -> Context {
    let chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc";
    let keys = crypto::derive_epoch_keys(
        &decode(chain, Some(32)).unwrap().try_into().unwrap(),
        0,
        &std::array::from_fn(|n| 0xa0 + n as u8),
    )
    .unwrap();
    Context {
        chain_id: chain.into(),
        generation: 0,
        epoch: 0,
        epoch_commitment: keys.epoch_commitment_b64u.clone(),
        metadata_profile: profile.into(),
    }
}

#[test]
fn commitment_and_profile_are_checked_before_plaintext_is_returned_and_closed_sessions_stay_closed()
{
    let secret = std::array::from_fn::<_, 32, _>(|n| 0xa0 + n as u8);
    let mut wrong = context("private");
    wrong.epoch_commitment = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".into();
    assert_eq!(
        epoch_session::begin(wrong, &secret).unwrap_err().0,
        "C_COMMITMENT_MISMATCH"
    );
    let chain = context("private").chain_id;
    let keys = crypto::derive_epoch_keys(
        &decode(&chain, Some(32)).unwrap().try_into().unwrap(),
        0,
        &secret,
    )
    .unwrap();
    let inner = InnerRecord {
        collection: "palleria.search_history/3".into(),
        device_seq: 1,
        prev_record_hash: None,
        operations: vec![Operation {
            entity_id: "猫".into(),
            operation: "upsert".into(),
            lamport: 1,
            created_at_ms: 1,
            context: json!({}),
            body: json!({"query":"猫"}),
            ext: None,
        }],
        ext: None,
    };
    let handle = epoch_session::begin(context("queryable"), &secret).unwrap();
    let tag = epoch_session::tag(handle, &inner.collection)
        .unwrap()
        .unwrap();
    let sign = SigningKey::from_bytes(&[1; 32]);
    let make = |tag: Option<&str>| {
        crypto::create_sync_record(
            &chain,
            0,
            "11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222",
            0,
            tag,
            &inner,
            &keys.record_key,
            &sign,
            false,
        )
        .unwrap()
    };
    let record = serde_json::to_value(make(Some(&tag))).unwrap();
    assert_eq!(
        epoch_session::decrypt_record(handle, &record)
            .unwrap()
            .operations[0]
            .body,
        inner.operations[0].body
    );
    let mismatched = serde_json::to_value(make(Some(&crypto::compute_collection_tag(
        &keys.collection_tag_key,
        "palleria.view_history/3",
    ))))
    .unwrap();
    assert_eq!(
        epoch_session::decrypt_record(handle, &mismatched)
            .unwrap_err()
            .0,
        "C_COLLECTION_TAG_MISMATCH"
    );
    assert_eq!(
        epoch_session::decrypt_record(handle, &serde_json::to_value(make(None)).unwrap())
            .unwrap_err()
            .0,
        "C_SCHEMA_VIOLATION"
    );
    epoch_session::end(handle);
    assert_eq!(
        epoch_session::decrypt_record(handle, &record)
            .unwrap_err()
            .0,
        "C_MISSING_DEPENDENCY"
    );
}

#[test]
fn snapshot_records_use_reserved_collection_signature_and_plaintext_validation() {
    let secret = std::array::from_fn::<_, 32, _>(|n| 0xa0 + n as u8);
    let chain = context("queryable").chain_id;
    let keys = crypto::derive_epoch_keys(
        &decode(&chain, Some(32)).unwrap().try_into().unwrap(),
        0,
        &secret,
    )
    .unwrap();
    let part = json!({
        "type":"PALLASYNC-SNAPSHOT-v3",
        "collection":"pallasync.snapshot/3",
        "compaction_id":"33333333-3333-4333-8333-333333333333",
        "part_index":0,
        "part_count":1,
        "entries":[{
            "kind":"header",
            "chain_id":chain,
            "generation":0,
            "log_id":"44444444-4444-4444-8444-444444444444",
            "covered_through_seq":0,
            "covered_log_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "epoch_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "lamport_max":0
        }]
    });
    let handle = epoch_session::begin(context("queryable"), &secret).unwrap();
    let value = epoch_session::create_snapshot_record(
        handle,
        "11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222",
        &part,
        &[9; 32],
    )
    .unwrap();
    assert_eq!(
        epoch_session::decrypt_snapshot_part(
            handle,
            &value,
            &URL_SAFE_NO_PAD.encode(SigningKey::from_bytes(&[9; 32]).verifying_key().as_bytes()),
        )
        .unwrap(),
        part
    );
    assert_eq!(
        epoch_session::decrypt_snapshot_part(
            handle,
            &value,
            &URL_SAFE_NO_PAD.encode(SigningKey::from_bytes(&[10; 32]).verifying_key().as_bytes()),
        )
        .unwrap_err()
        .0,
        "C_SIGNATURE_INVALID"
    );
    assert_eq!(
        value["collection_tag"],
        crypto::compute_collection_tag(&keys.collection_tag_key, "pallasync.snapshot/3")
    );
    epoch_session::end(handle);
}
