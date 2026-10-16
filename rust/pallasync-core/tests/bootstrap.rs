use pallasync_core::{bootstrap::*, client_response::initial_parameters, strict::jcs};
use serde_json::json;

#[test]
fn selected_complete_history_binds_receipts_recovery_envelope_and_signed_cut() {
    let f = jcs::parse(include_bytes!("fixtures/python-genesis-acceptance.json")).unwrap();
    let mut authority = initial_parameters(
        &f["parameters"],
        &f["identity"],
        f["origin"].as_str().unwrap(),
        f["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    let receipts = f["created"]["upload_receipts"]
        .as_array()
        .unwrap()
        .iter()
        .chain(
            f["record_upload"]["upload_receipts"]
                .as_array()
                .unwrap()
                .iter(),
        )
        .collect::<Vec<_>>();
    for page in f["log_pages"].as_array().unwrap() {
        for entry in page["items"].as_array().unwrap() {
            let receipt = receipts
                .iter()
                .find(|r| r["relay_seq"] == entry["relay_seq"])
                .unwrap();
            let response = json!({"entry":entry,"receipt":receipt});
            let checked = receipt_response(&response, entry, &authority.pin).unwrap();
            assert_eq!(
                retained_log_link(
                    entry,
                    &checked,
                    &authority.pin,
                    authority.sequence,
                    &authority.head_hash
                )
                .unwrap(),
                checked["resulting_head_hash"].as_str().unwrap(),
            );
            assert_eq!(
                retained_log_link(
                    entry,
                    &checked,
                    &authority.pin,
                    authority.sequence + 1,
                    &authority.head_hash
                )
                .unwrap_err()
                .0,
                "C_MISSING_DEPENDENCY",
            );
            let mut tampered = response.clone();
            tampered["entry"] = serde_json::Value::Null;
            assert_eq!(
                receipt_response(&tampered, entry, &authority.pin)
                    .unwrap_err()
                    .0,
                "C_ACCEPTANCE_UNPROVEN"
            );
            authority = authority.advance(entry, &checked).unwrap();
        }
    }
    let response = json!({"head":f["transparency_head"]});
    let cut = history_cut(&authority, &response).unwrap();
    assert_eq!(cut["zero_anchor"]["index"], 0);
    assert_eq!(cut["generation"], 0);
    let mut shortened = authority.clone();
    shortened.sequence -= 1;
    assert_eq!(
        history_cut(&shortened, &response).unwrap_err().0,
        "C_MISSING_DEPENDENCY"
    );
    let env = f["genesis"]["envelopes"]
        .as_array()
        .unwrap()
        .iter()
        .find(|e| e["recipient_kind"] == "recovery")
        .unwrap();
    let mut response = json!({"envelope":env});
    assert_eq!(recovery_response(&response, &authority, 0).unwrap(), *env);
    response["envelope"]["ciphertext"] = json!("AA");
    assert!(recovery_response(&response, &authority, 0).is_err());
    let ack = json!({"device_id":f["genesis"]["device_record"]["device_id"],"status":"active","epoch_hash":f["created"]["epoch_hash"]});
    keys_ack(
        &ack,
        ack["device_id"].as_str().unwrap(),
        ack["epoch_hash"].as_str().unwrap(),
    )
    .unwrap();
    assert!(
        keys_ack(
            &ack,
            ack["device_id"].as_str().unwrap(),
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        )
        .is_err()
    );
}

#[test]
fn complete_authority_can_reconcile_a_signed_pruned_head_for_snapshot_bootstrap() {
    use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};

    let f = jcs::parse(include_bytes!("fixtures/python-genesis-acceptance.json")).unwrap();
    let mut authority = initial_parameters(
        &f["parameters"],
        &f["identity"],
        f["origin"].as_str().unwrap(),
        f["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    let receipts = f["created"]["upload_receipts"]
        .as_array()
        .unwrap()
        .iter()
        .chain(f["record_upload"]["upload_receipts"].as_array().unwrap())
        .collect::<Vec<_>>();
    for page in f["log_pages"].as_array().unwrap() {
        for entry in page["items"].as_array().unwrap() {
            let receipt = receipts
                .iter()
                .find(|receipt| receipt["relay_seq"] == entry["relay_seq"])
                .unwrap();
            authority = authority.advance(entry, receipt).unwrap();
        }
    }

    let relay = ed25519_dalek::SigningKey::from_bytes(&[0x6d; 32]);
    authority.pin.public_key = URL_SAFE_NO_PAD.encode(relay.verifying_key().as_bytes());
    let mut head = f["transparency_head"].clone();
    head["relay_key_id"] = json!(authority.pin.key_id().unwrap());
    head["min_available_seq"] = json!(2);
    let mut unsigned = head.clone();
    unsigned.as_object_mut().unwrap().remove("relay_signature");
    head["relay_signature"] = json!(pallasync_core::crypto::sign_with_context(
        &relay,
        b"PALLASYNC-TRANSPARENCY-HEAD-v3\0",
        &jcs::canonicalize_json(&unsigned).unwrap(),
    ));

    let cut = history_cut(&authority, &json!({"head":head})).unwrap();
    assert_eq!(cut["head"]["min_available_seq"], 2);
    assert_eq!(cut["head"]["head_seq"], authority.sequence);

    let mut stale = authority.clone();
    stale.sequence -= 1;
    assert_eq!(
        history_cut(&stale, &json!({"head":head})).unwrap_err().0,
        "C_MISSING_DEPENDENCY"
    );
}

#[test]
fn independent_enrollment_receipts_cover_the_exact_complete_bundle_and_continue_authority() {
    let f = jcs::parse(include_bytes!("fixtures/python-genesis-acceptance.json")).unwrap();
    let mut authority = pallasync_core::authority::Authority::from_genesis(
        &f["created"],
        &f["genesis"],
        &f["identity"],
        f["origin"].as_str().unwrap(),
        f["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    let record = &f["log_pages"][1]["items"][2];
    authority = authority
        .advance(record, &f["record_upload"]["upload_receipts"][0])
        .unwrap();
    let enrol = &f["enrollment"];
    enrollment_acceptance(&enrol["accepted"], &enrol["body"], &authority.pin).unwrap();
    let mut dropped = enrol["accepted"].clone();
    dropped["upload_receipts"].as_array_mut().unwrap().pop();
    assert_eq!(
        enrollment_acceptance(&dropped, &enrol["body"], &authority.pin)
            .unwrap_err()
            .0,
        "C_ACCEPTANCE_UNPROVEN"
    );
    for page in enrol["log_pages"].as_array().unwrap() {
        for entry in page["items"].as_array().unwrap() {
            let r = enrol["accepted"]["upload_receipts"]
                .as_array()
                .unwrap()
                .iter()
                .find(|r| r["relay_seq"] == entry["relay_seq"])
                .unwrap();
            authority = authority.advance(entry, r).unwrap();
        }
    }
    assert_eq!(authority.sequence, 9);
    let cut = history_cut(&authority, &json!({"head":enrol["transparency_head"]})).unwrap();
    assert_eq!(cut["generation"], 0);
    let device = enrol["body"]["device_record"]["device_id"]
        .as_str()
        .unwrap();
    authority
        .known_device(&enrol["body"]["device_record"])
        .unwrap();
    keys_ack(
        &enrol["acked"],
        device,
        enrol["ack_body"]["epoch_hash"].as_str().unwrap(),
    )
    .unwrap();
}

#[test]
fn root_enrollment_session_can_read_the_specified_transparency_entries_route_only() {
    use pallasync_core::root_session::{self, Operation};
    let mnemonic = format!("{} art", vec!["abandon"; 23].join(" "));
    let handle =
        root_session::begin(mnemonic.as_bytes(), b"", Operation::Enrollment, None, None).unwrap();
    let chain = root_session::identity(handle).unwrap()["chain_id"]
        .as_str()
        .unwrap()
        .to_owned();
    let prefix = format!("/pallasync/v3/chains/{chain}");
    assert!(
        root_session::capability(
            handle,
            Operation::Enrollment,
            "https://relay.example.com",
            "GET",
            &format!("{prefix}/transparency/entries"),
            "transparency_id=11111111-1111-4111-8111-111111111111&after_index=0",
            b""
        )
        .is_ok()
    );
    assert_eq!(
        root_session::capability(
            handle,
            Operation::Enrollment,
            "https://relay.example.com",
            "GET",
            &format!("{prefix}/transparency"),
            "",
            b""
        )
        .unwrap_err()
        .0,
        "C_RECOVERY_AUTH_REQUIRED"
    );
    root_session::end(handle);
}

#[test]
fn root_reads_use_only_canonical_catalogue_paths_and_the_corrected_two_minute_clock() {
    use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
    use pallasync_core::root_session::{self, Operation};
    let phrase = format!("{} art", vec!["abandon"; 23].join(" "));
    let handle =
        root_session::begin(phrase.as_bytes(), b"", Operation::Enrollment, None, None).unwrap();
    let chain = root_session::identity(handle).unwrap()["chain_id"]
        .as_str()
        .unwrap()
        .to_owned();
    let prefix = format!("/pallasync/v3/chains/{chain}");
    for suffix in [
        "/epochs/0",
        "/epochs/4294967295/recovery-envelope",
        "/receipts/1",
        "/export",
        "/deletion",
        "/snapshots/parts/11111111-1111-4111-8111-111111111111",
        "/devices/11111111-1111-4111-8111-111111111111/envelopes",
    ] {
        let token = root_session::capability_at(
            handle,
            Operation::Enrollment,
            "https://relay.example.com",
            "GET",
            &format!("{prefix}{suffix}"),
            "",
            b"",
            1791684000000,
        )
        .unwrap();
        let token = jcs::parse(&URL_SAFE_NO_PAD.decode(token).unwrap()).unwrap();
        assert_eq!(token["issued_at_ms"], 1791684000000u64);
        assert_eq!(token["expires_at_ms"], 1791684120000u64);
    }
    for suffix in [
        "/epochs/00",
        "/epochs/4294967296/recovery-envelope",
        "/epochs/invalid/recovery-envelope",
        "/receipts/0",
        "/receipts/01",
        "/receipts/1/other",
        "/snapshots/parts/unknown",
        "/devices/unknown/envelopes",
    ] {
        assert_eq!(
            root_session::capability_at(
                handle,
                Operation::Enrollment,
                "https://relay.example.com",
                "GET",
                &format!("{prefix}{suffix}"),
                "",
                b"",
                1791684000000
            )
            .unwrap_err()
            .0,
            "C_RECOVERY_AUTH_REQUIRED"
        );
    }
    root_session::end(handle);
}

#[test]
fn reseed_root_capability_is_limited_to_import_reseed_and_admin_repair_routes() {
    use pallasync_core::root_session::{self, Operation};
    let phrase = format!("{} art", vec!["abandon"; 23].join(" "));
    let handle =
        root_session::begin(phrase.as_bytes(), b"", Operation::Reseed, None, None).unwrap();
    let chain = root_session::identity(handle).unwrap()["chain_id"]
        .as_str()
        .unwrap()
        .to_owned();
    let prefix = format!("/pallasync/v3/chains/{chain}");
    for (method, path) in [
        ("POST", "/pallasync/v3/chains/import".to_owned()),
        ("POST", format!("{prefix}/records/repair")),
        ("PUT", format!("{prefix}/parameters")),
    ] {
        assert!(
            root_session::capability_at(
                handle,
                Operation::Reseed,
                "https://relay.example.com",
                method,
                &path,
                "",
                b"{}",
                1791684000000,
            )
            .is_ok()
        );
    }
    for (method, path) in [
        ("POST", format!("{prefix}/records")),
        ("POST", format!("{prefix}/devices/enroll")),
        ("DELETE", prefix.clone()),
    ] {
        assert_eq!(
            root_session::capability_at(
                handle,
                Operation::Reseed,
                "https://relay.example.com",
                method,
                &path,
                "",
                b"{}",
                1791684000000,
            )
            .unwrap_err()
            .0,
            "C_RECOVERY_AUTH_REQUIRED"
        );
    }
    root_session::end(handle);
}

#[test]
fn export_root_capability_is_limited_to_the_chain_export_route() {
    use pallasync_core::root_session::{self, Operation};
    let phrase = format!("{} art", vec!["abandon"; 23].join(" "));
    let handle =
        root_session::begin(phrase.as_bytes(), b"", Operation::Export, None, None).unwrap();
    let chain = root_session::identity(handle).unwrap()["chain_id"]
        .as_str()
        .unwrap()
        .to_owned();
    let prefix = format!("/pallasync/v3/chains/{chain}");
    assert!(
        root_session::capability_at(
            handle,
            Operation::Export,
            "https://relay.example.com",
            "GET",
            &format!("{prefix}/export"),
            "include_recovery=true&limit=200",
            b"",
            1791684000000,
        )
        .is_ok()
    );
    for path in [
        format!("{prefix}/parameters"),
        format!("{prefix}/records/repair"),
    ] {
        assert_eq!(
            root_session::capability_at(
                handle,
                Operation::Export,
                "https://relay.example.com",
                "GET",
                &path,
                "",
                b"",
                1791684000000,
            )
            .unwrap_err()
            .0,
            "C_RECOVERY_AUTH_REQUIRED"
        );
    }
    root_session::end(handle);
}
