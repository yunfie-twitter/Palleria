use pallasync_core::{
    attestations::*,
    authority::{AcceptedEpoch, Admission, Authority},
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
fn snapshot_part_readback_binds_signed_record_and_unchanged_data_head() {
    let relay = ed25519_dalek::SigningKey::from_bytes(&[0x51; 32]);
    let author = ed25519_dalek::SigningKey::from_bytes(&[0x52; 32]);
    let chain_id = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc";
    let log_id = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    let head_hash = hash_bytes(&[0x53; 32]);
    let pin = RelayPin {
        origin: "https://relay.example.com".into(),
        chain_id: chain_id.into(),
        public_key: base64::Engine::encode(
            &base64::engine::general_purpose::URL_SAFE_NO_PAD,
            relay.verifying_key().as_bytes(),
        ),
        log_id: log_id.into(),
    };
    let part = json!({
        "type":"PALLASYNC-SNAPSHOT-v3",
        "collection":"pallasync.snapshot/3",
        "compaction_id":"55555555-5555-4555-8555-555555555555",
        "part_index":0,
        "part_count":1,
        "entries":[]
    });
    let record = pallasync_core::crypto::create_sync_snapshot_record(
        chain_id,
        0,
        "11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222",
        0,
        None,
        &part,
        &[0x54; 32],
        &author,
        true,
    )
    .unwrap();
    let record = serde_json::to_value(record).unwrap();
    let mut receipt = json!({
        "type":"PALLASYNC-UPLOAD-RECEIPT-v3","version":"3.0",
        "receipt_id":"66666666-6666-4666-8666-666666666666","chain_id":chain_id,
        "log_id":log_id,"relay_key_id":pin.key_id().unwrap(),"relay_seq":null,
        "object_hash":object_hash(&record),"entry_hash":null,
        "previous_head_hash":head_hash,"resulting_head_hash":head_hash,
        "received_at_ms":1791684000000u64,"transparency_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd",
        "transparency_index":1,"transparency_hash":hash_bytes(&[0x55;32]),"relay_signature":""
    });
    sign_relay(&relay, &mut receipt, CTX_RECEIPT).unwrap();
    let response = json!({"record":record,"upload_receipt":receipt});
    assert_eq!(
        validate_snapshot_part_response(&response, "11111111-1111-4111-8111-111111111111", &pin,)
            .unwrap(),
        response
    );
    let mut changed_head = response.clone();
    changed_head["upload_receipt"]["resulting_head_hash"] = hash_bytes(&[0x56; 32]).into();
    sign_relay(&relay, &mut changed_head["upload_receipt"], CTX_RECEIPT).unwrap();
    assert_eq!(
        validate_snapshot_part_response(
            &changed_head,
            "11111111-1111-4111-8111-111111111111",
            &pin,
        )
        .unwrap_err()
        .0,
        "C_ACCEPTANCE_UNPROVEN"
    );
}

#[test]
fn snapshot_part_upload_requires_exact_id_coverage_and_null_sequence_receipts() {
    let relay = ed25519_dalek::SigningKey::from_bytes(&[0x61; 32]);
    let author = ed25519_dalek::SigningKey::from_bytes(&[0x62; 32]);
    let chain_id = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc";
    let log_id = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    let pin = RelayPin {
        origin: "https://relay.example.com".into(),
        chain_id: chain_id.into(),
        public_key: base64::Engine::encode(
            &base64::engine::general_purpose::URL_SAFE_NO_PAD,
            relay.verifying_key().as_bytes(),
        ),
        log_id: log_id.into(),
    };
    let part = json!({
        "type":"PALLASYNC-SNAPSHOT-v3",
        "collection":"pallasync.snapshot/3",
        "compaction_id":"55555555-5555-4555-8555-555555555555",
        "part_index":0,
        "part_count":2,
        "entries":[]
    });
    let records: Vec<Value> = [
        "11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222",
    ]
    .into_iter()
    .map(|id| {
        serde_json::to_value(
            pallasync_core::crypto::create_sync_snapshot_record(
                chain_id,
                0,
                id,
                "33333333-3333-4333-8333-333333333333",
                0,
                None,
                &part,
                &[0x63; 32],
                &author,
                true,
            )
            .unwrap(),
        )
        .unwrap()
    })
    .collect();
    let head_hash = hash_bytes(&[0x64; 32]);
    let mut receipts: Vec<Value> = records
        .iter()
        .enumerate()
        .map(|(index, record)| {
            let mut receipt = json!({
                "type":"PALLASYNC-UPLOAD-RECEIPT-v3","version":"3.0",
                "receipt_id":format!("66666666-6666-4666-8666-66666666666{}", index + 1),
                "chain_id":chain_id,"log_id":log_id,"relay_key_id":pin.key_id().unwrap(),
                "relay_seq":null,"object_hash":object_hash(record),"entry_hash":null,
                "previous_head_hash":head_hash,"resulting_head_hash":head_hash,
                "received_at_ms":1791684000000u64,"transparency_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd",
                "transparency_index":index + 1,"transparency_hash":hash_bytes(&[0x65 + index as u8;32]),
                "relay_signature":""
            });
            sign_relay(&relay, &mut receipt, CTX_RECEIPT).unwrap();
            receipt
        })
        .collect();
    // UUID text prefix order is the specified receipt order for these fixture IDs.
    let response = json!({"accepted":[
        "11111111-1111-4111-8111-111111111111",
        "22222222-2222-4222-8222-222222222222"
    ],"rejected":[],"upload_receipts":receipts});
    assert_eq!(
        validate_snapshot_part_upload(&response, &records, &pin).unwrap(),
        response
    );

    let mut omitted = response.clone();
    omitted["upload_receipts"].as_array_mut().unwrap().pop();
    assert_eq!(
        validate_snapshot_part_upload(&omitted, &records, &pin)
            .unwrap_err()
            .0,
        "C_ACCEPTANCE_UNPROVEN"
    );

    receipts.reverse();
    let mut reordered = response.clone();
    reordered["upload_receipts"] = receipts.into();
    assert_eq!(
        validate_snapshot_part_upload(&reordered, &records, &pin)
            .unwrap_err()
            .0,
        "C_SCHEMA_VIOLATION"
    );

    let mut accepted_rejected = response.clone();
    accepted_rejected["rejected"] = json!([{
        "record_id":"11111111-1111-4111-8111-111111111111",
        "status":409,"code":"RECORD_ID_CONFLICT","retryable":false
    }]);
    assert_eq!(
        validate_snapshot_part_upload(&accepted_rejected, &records, &pin)
            .unwrap_err()
            .0,
        "C_ACCEPTANCE_UNPROVEN"
    );
}

#[test]
fn compaction_publish_requires_exact_compaction_log_entry_and_signed_receipt() {
    let relay = ed25519_dalek::SigningKey::from_bytes(&[0x71; 32]);
    let author = ed25519_dalek::SigningKey::from_bytes(&[0x72; 32]);
    let pin = RelayPin {
        origin: "https://relay.example.com".into(),
        chain_id: "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc".into(),
        public_key: base64::Engine::encode(
            &base64::engine::general_purpose::URL_SAFE_NO_PAD,
            relay.verifying_key().as_bytes(),
        ),
        log_id: "cccccccc-cccc-4ccc-8ccc-cccccccccccc".into(),
    };
    let mut compaction = json!({
        "compaction_id":"55555555-5555-4555-8555-555555555555",
        "chain_id":pin.chain_id,
        "generation":0,
        "device_id":"22222222-2222-4222-8222-222222222222",
        "log_id":pin.log_id,
        "covered_through_seq":6,
        "covered_log_hash":hash_bytes(&[0x73;32]),
        "epoch_hash":hash_bytes(&[0x74;32]),
        "snapshot_parts":[{"record_id":"11111111-1111-4111-8111-111111111111","record_hash":hash_bytes(&[0x75;32])}],
        "state_sha256":hash_bytes(&[0x76;32]),
        "signature":""
    });
    let canonical = jcs::canonicalize_json(&compaction).unwrap();
    compaction["signature"] = json!(pallasync_core::crypto::sign_with_context(
        &author,
        CTX_COMPACTION,
        &canonical,
    ));
    let entry = json!({
        "relay_seq":7,
        "received_at_ms":1791684000000u64,
        "kind":"compaction",
        "object":compaction
    });
    let previous_head = hash_bytes(&[0x77; 32]);
    let resulting_head = data_link(&previous_head, &entry).unwrap();
    let mut receipt = json!({
        "type":"PALLASYNC-UPLOAD-RECEIPT-v3","version":"3.0",
        "receipt_id":"66666666-6666-4666-8666-666666666666",
        "chain_id":pin.chain_id,"log_id":pin.log_id,"relay_key_id":pin.key_id().unwrap(),
        "relay_seq":7,"object_hash":object_hash(&compaction),"entry_hash":object_hash(&entry),
        "previous_head_hash":previous_head,"resulting_head_hash":resulting_head,
        "received_at_ms":1791684000000u64,"transparency_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd",
        "transparency_index":8,"transparency_hash":hash_bytes(&[0x78;32]),"relay_signature":""
    });
    sign_relay(&relay, &mut receipt, CTX_RECEIPT).unwrap();
    let response = json!({"compaction_id":compaction["compaction_id"],"receipt":entry,"upload_receipts":[receipt]});
    assert_eq!(
        validate_compaction_publish_response(&response, &compaction, &pin).unwrap(),
        response
    );

    let mut wrong_object = response.clone();
    wrong_object["receipt"]["object"]["state_sha256"] = json!(hash_bytes(&[0x79; 32]));
    assert_eq!(
        validate_compaction_publish_response(&wrong_object, &compaction, &pin)
            .unwrap_err()
            .0,
        "C_ACCEPTANCE_UNPROVEN"
    );

    let mut missing_attestation = response.clone();
    missing_attestation["upload_receipts"] = json!([]);
    assert_eq!(
        validate_compaction_publish_response(&missing_attestation, &compaction, &pin)
            .unwrap_err()
            .0,
        "C_ACCEPTANCE_UNPROVEN"
    );
}

fn deletion_evidence_fixture() -> (RelayPin, Authority, Value, Value, ed25519_dalek::SigningKey) {
    let relay = ed25519_dalek::SigningKey::from_bytes(&[0x41; 32]);
    let admin = ed25519_dalek::SigningKey::from_bytes(&[0x42; 32]);
    let device = ed25519_dalek::SigningKey::from_bytes(&[0x43; 32]);
    let chain_id = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc";
    let log_id = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    let device_id = "11111111-1111-4111-8111-111111111111";
    let pin = RelayPin {
        origin: "https://relay.example.com".into(),
        chain_id: chain_id.into(),
        public_key: base64::Engine::encode(
            &base64::engine::general_purpose::URL_SAFE_NO_PAD,
            relay.verifying_key().as_bytes(),
        ),
        log_id: log_id.into(),
    };
    let parameters = json!({"chain_id":chain_id,"generation":0});
    let epoch = json!({"chain_id":chain_id,"generation":0,"members":[device_id]});
    let admin_public = base64::Engine::encode(
        &base64::engine::general_purpose::URL_SAFE_NO_PAD,
        admin.verifying_key().as_bytes(),
    );
    let mut authority = Authority::new(pin.clone(), admin_public).unwrap();
    authority.parameters = Some(parameters.clone());
    authority.devices.insert(
        device_id.into(),
        Admission {
            record: json!({
                "generation":0,
                "device_public_key":base64::Engine::encode(
                    &base64::engine::general_purpose::URL_SAFE_NO_PAD,
                    device.verifying_key().as_bytes(),
                )
            }),
            enrolled_seq: 1,
            revoked_seq: None,
        },
    );
    authority.epochs.insert(
        0,
        AcceptedEpoch {
            record: epoch.clone(),
            relay_seq: 5,
            committed_at_ms: 1,
        },
    );
    let mut receipt = json!({
        "type":"PALLASYNC-DELETE-RECEIPT-v3","version":"3.0","chain_id":chain_id,
        "deletion_id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","log_id":log_id,
        "relay_key_id":pin.key_id().unwrap(),"authorized_parameters_hash":object_hash(&parameters),
        "authorized_epoch_hash":object_hash(&epoch),"authorization_hash":hash_bytes(&[7;32]),
        "requested_at_ms":10,"completed_at_ms":null,"assurance":"crypto-shred","delete_after_ms":null,
        "ledger_receipt_hash":hash_bytes(&[8;32]),"transparency_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd",
        "transparency_index":1,"transparency_hash":hash_bytes(&[9;32]),"relay_signature":""
    });
    sign_relay(&relay, &mut receipt, CTX_DELETE).unwrap();
    let bundle = json!({
        "type":"PALLASYNC-DELETION-EVIDENCE-v3","version":"3.0","chain_id":chain_id,
        "deletion_id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        "delete_receipt_hash":object_hash(&receipt),"witnesses":[]
    });
    (pin, authority, receipt, bundle, relay)
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
    let mut repair_response = response.clone();
    repair_response["repair_required"] = json!(false);
    assert!(!verify_record_repair_response(&repair_response, &[vector("record")], &pin).unwrap());
    repair_response["repair_required"] = json!(true);
    assert!(verify_record_repair_response(&repair_response, &[vector("record")], &pin).unwrap());
    let mut missing_repair_status = response.clone();
    assert!(
        verify_record_repair_response(&missing_repair_status, &[vector("record")], &pin).is_err()
    );
    missing_repair_status["repair_required"] = json!("true");
    assert!(
        verify_record_repair_response(&missing_repair_status, &[vector("record")], &pin).is_err()
    );
    let mut invalid_repair = repair_response.clone();
    invalid_repair["upload_receipts"] = json!([]);
    assert!(verify_record_repair_response(&invalid_repair, &[vector("record")], &pin).is_err());
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

#[test]
fn deletion_evidence_bundle_keeps_receipt_immutable_and_binds_exact_hash() {
    let (_, authority, receipt, bundle, _) = deletion_evidence_fixture();
    validate_deletion_evidence_bundle(&receipt, &bundle, &authority).unwrap();

    let mut wrong_receipt_hash = bundle.clone();
    wrong_receipt_hash["delete_receipt_hash"] = json!(hash_bytes(&[0; 32]));
    assert_eq!(
        validate_deletion_evidence_bundle(&receipt, &wrong_receipt_hash, &authority)
            .unwrap_err()
            .0,
        "C_DELETION_UNVERIFIED"
    );

    let mut receipt_with_witnesses = receipt.clone();
    receipt_with_witnesses["witness_signatures"] = json!([]);
    assert_eq!(
        validate_deletion_evidence_bundle(&receipt_with_witnesses, &bundle, &authority)
            .unwrap_err()
            .0,
        "C_SCHEMA_VIOLATION"
    );
}

#[test]
fn deletion_witness_requires_current_certified_device_seed_and_binds_receipt() {
    let (_, authority, receipt, mut bundle, relay) = deletion_evidence_fixture();
    let mut seed = [0x43; 32];
    let witness = create_deletion_witness(
        &receipt,
        &authority,
        "11111111-1111-4111-8111-111111111111",
        &mut seed,
        11,
    )
    .unwrap();
    assert!(seed.iter().all(|byte| *byte == 0));
    bundle["witnesses"] = json!([witness.clone()]);
    validate_deletion_evidence_bundle(&receipt, &bundle, &authority).unwrap();

    let mut wrong_key_seed = [0x44; 32];
    assert_eq!(
        create_deletion_witness(
            &receipt,
            &authority,
            "11111111-1111-4111-8111-111111111111",
            &mut wrong_key_seed,
            11,
        )
        .unwrap_err()
        .0,
        "C_DEVICE_KEY_MISMATCH"
    );
    let mut completed_receipt = receipt.clone();
    completed_receipt["completed_at_ms"] = json!(12);
    sign_relay(&relay, &mut completed_receipt, CTX_DELETE).unwrap();
    assert_eq!(
        validate_deletion_evidence_bundle(&completed_receipt, &bundle, &authority)
            .unwrap_err()
            .0,
        "C_DELETION_UNVERIFIED"
    );
}

#[test]
fn deletion_evidence_accepts_128_certified_witnesses_and_rejects_129_as_schema() {
    let (_, mut authority, mut receipt, mut bundle, relay) = deletion_evidence_fixture();
    let mut members = Vec::with_capacity(128);
    let mut devices = Vec::with_capacity(128);
    for index in 0..128u16 {
        let device_id = format!("00000000-0000-4000-8000-{index:012x}");
        let seed = [index as u8 + 1; 32];
        let device = ed25519_dalek::SigningKey::from_bytes(&seed);
        authority.devices.insert(
            device_id.clone(),
            Admission {
                record: json!({
                    "generation":0,
                    "device_public_key":base64::Engine::encode(
                        &base64::engine::general_purpose::URL_SAFE_NO_PAD,
                        device.verifying_key().as_bytes(),
                    )
                }),
                enrolled_seq: 1,
                revoked_seq: None,
            },
        );
        members.push(json!(device_id));
        devices.push((device_id, seed));
    }
    authority.epochs.get_mut(&0).unwrap().record["members"] = json!(members);
    receipt["authorized_epoch_hash"] = json!(object_hash(&authority.epochs[&0].record));
    sign_relay(&relay, &mut receipt, CTX_DELETE).unwrap();
    bundle["delete_receipt_hash"] = json!(object_hash(&receipt));
    let mut witnesses = Vec::with_capacity(128);
    for (device_id, mut seed) in devices {
        witnesses.push(
            create_deletion_witness(&receipt, &authority, &device_id, &mut seed, 11).unwrap(),
        );
    }
    bundle["witnesses"] = json!(witnesses);
    validate_deletion_evidence_bundle(&receipt, &bundle, &authority).unwrap();

    bundle["witnesses"] = json!(vec![json!({}); 129]);
    assert_eq!(
        validate_deletion_evidence_bundle(&receipt, &bundle, &authority)
            .unwrap_err()
            .0,
        "C_SCHEMA_VIOLATION"
    );
}

#[test]
fn deletion_completion_receipt_revision_is_signed_monotone_and_keeps_original() {
    let (pin, _, original, _, _) = deletion_evidence_fixture();
    let relay = ed25519_dalek::SigningKey::from_bytes(&[0x41; 32]);
    let mut completed = original.clone();
    completed["completed_at_ms"] = json!(20);
    completed["ledger_receipt_hash"] = json!(hash_bytes(&[10; 32]));
    sign_relay(&relay, &mut completed, CTX_DELETE).unwrap();

    validate_deletion_receipt_revision(&original, &completed, &pin).unwrap();
    assert_eq!(original["completed_at_ms"], Value::Null);

    let mut regressed = completed.clone();
    regressed["completed_at_ms"] = json!(19);
    sign_relay(&relay, &mut regressed, CTX_DELETE).unwrap();
    assert_eq!(
        validate_deletion_receipt_revision(&completed, &regressed, &pin)
            .unwrap_err()
            .0,
        "C_DELETION_UNVERIFIED"
    );

    let mut changed_binding = completed.clone();
    changed_binding["transparency_hash"] = json!(hash_bytes(&[11; 32]));
    sign_relay(&relay, &mut changed_binding, CTX_DELETE).unwrap();
    assert_eq!(
        validate_deletion_receipt_revision(&original, &changed_binding, &pin)
            .unwrap_err()
            .0,
        "C_DELETION_UNVERIFIED"
    );
}
