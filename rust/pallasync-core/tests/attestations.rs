use pallasync_core::{
    attestations::*,
    strict::{jcs, verify::*},
};
use serde_json::{Value, json};

fn vector(name: &str) -> Value {
    let bytes: &[u8] = match name {
        "record" => include_bytes!("fixtures/attestations/record.json"),
        "receipt" => include_bytes!("fixtures/attestations/receipt.json"),
        "transparency" => include_bytes!("fixtures/attestations/transparency.json"),
        _ => unreachable!(),
    };
    jcs::parse(bytes).unwrap()
}
fn pin() -> RelayPin {
    RelayPin {
        origin: "https://relay.example.com".into(),
        chain_id: "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc".into(),
        public_key: "T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ".into(),
        log_id: "cccccccc-cccc-4ccc-8ccc-cccccccccccc".into(),
    }
}
fn entry() -> Value {
    json!({"relay_seq":7,"kind":"record","received_at_ms":1791684000000u64,"object":vector("record")})
}

#[test]
fn c9_exact_object_entry_data_and_transparency_commitments() {
    let pin = pin();
    let receipt = vector("receipt");
    let transparent = vector("transparency");
    assert_eq!(
        object_hash(&entry()),
        "eQkBDj_MnQbwW1LSjkwHmfTVRxUFYV0TxpYUFeeTqZo"
    );
    assert_eq!(
        data_link(receipt["previous_head_hash"].as_str().unwrap(), &entry()).unwrap(),
        "9rmeVaK9mN98hMLU6FDa4ZGL77Mgrd5fwceAipags-o"
    );
    let zero = transparency_zero(
        &pin.chain_id,
        transparent["transparency_id"].as_str().unwrap(),
        &pin.key_id().unwrap(),
    )
    .unwrap();
    assert_eq!(
        hex::encode(decode(&zero, Some(32)).unwrap()),
        "6ee2c315ded34a8bb92f0ee5511c22c2e83c6d9b79fe2dc1560535274dd7f29d"
    );
    assert_eq!(
        hex::encode(
            decode(
                &transparency_link(&zero, &transparent, &pin).unwrap(),
                Some(32)
            )
            .unwrap()
        ),
        "450fe12939119e4befe02d7ed25a063c8e76323605f53b2d8ad016fe16d890de"
    );
    verify_receipt(&receipt, &vector("record"), Some(&entry()), &pin).unwrap();
    verify_receipt_transparency(&receipt, &transparent, &pin).unwrap();
    let response = json!({"accepted":[{"record_id":vector("record")["record_id"],"duplicate":false,"receipt":entry()}],"rejected":[],"upload_receipts":[receipt],"generation":0,"log_id":pin.log_id,"head_seq":7,"head_hash":"9rmeVaK9mN98hMLU6FDa4ZGL77Mgrd5fwceAipags-o"});
    verify_record_upload_response(&response, &[vector("record")], &pin).unwrap();
    let mut missing = response.clone();
    missing["upload_receipts"] = json!([]);
    assert!(verify_record_upload_response(&missing, &[vector("record")], &pin).is_err());
    let mut wrong = entry();
    wrong["received_at_ms"] = json!(1791684000001u64);
    assert!(verify_receipt(&vector("receipt"), &vector("record"), Some(&wrong), &pin).is_err());
    let mut substituted = vector("record");
    substituted["payload_nonce"] = json!(hash_bytes(&[8; 24]));
    assert!(verify_receipt(&vector("receipt"), &substituted, Some(&entry()), &pin).is_err());
}

#[test]
fn signed_page_does_not_skip_gaps_or_reset_a_pinned_prefix() {
    let pin = pin();
    let receipt = vector("receipt");
    let entry = vector("transparency");
    let key = ed25519_dalek::SigningKey::from_bytes(
        &(0xa0..=0xbf).collect::<Vec<u8>>().try_into().unwrap(),
    );
    let mut head = json!({"type":"PALLASYNC-TRANSPARENCY-HEAD-v3","version":"3.0","chain_id":pin.chain_id,"log_id":pin.log_id,"relay_key_id":pin.key_id().unwrap(),"transparency_id":receipt["transparency_id"],"entry_index":1,"transparency_hash":receipt["transparency_hash"],"head_seq":7,"head_hash":receipt["resulting_head_hash"],"parameters_hash":hash_bytes(&[1;32]),"epoch_hash":hash_bytes(&[2;32]),"min_available_seq":1,"timestamp":1791684000000u64});
    sign_relay(&key, &mut head, CTX_HEAD).unwrap();
    let anchor = TransparencyAnchor {
        transparency_id: receipt["transparency_id"].as_str().unwrap().into(),
        index: 0,
        hash: entry["previous_transparency_hash"].as_str().unwrap().into(),
    };
    let original = anchor.clone();
    let next =
        verify_transparency_page(&anchor, std::slice::from_ref(&entry), &head, &pin).unwrap();
    assert_eq!(next.index, 1);
    assert_eq!(anchor, original);
    assert_eq!(
        verify_transparency_page(&anchor, &[], &head, &pin)
            .unwrap_err()
            .0,
        "C_MISSING_DEPENDENCY"
    );
    assert!(
        verify_transparency_page(&anchor, &[entry.clone(), entry.clone()], &head, &pin).is_err()
    );
    let mut changed = entry.clone();
    changed["entry_index"] = json!(2);
    sign_relay(&key, &mut changed, CTX_TRANSPARENCY).unwrap();
    assert!(verify_transparency_page(&anchor, &[changed], &head, &pin).is_err());
    let mut wrong_context = entry.clone();
    sign_relay(&key, &mut wrong_context, CTX_RECEIPT).unwrap();
    assert_eq!(
        validate_transparency(&wrong_context, &pin).unwrap_err().0,
        "C_SIGNATURE_INVALID"
    );
    let changed_pin = RelayPin {
        public_key: hash_bytes(&[4; 32]),
        ..pin.clone()
    };
    assert!(validate_head(&head, &changed_pin).is_err());
    let changed_log = RelayPin {
        log_id: "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee".into(),
        ..pin
    };
    assert!(validate_head(&head, &changed_log).is_err());
}
