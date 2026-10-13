use pallasync_core::{attestations::RelayPin, client_response::*, strict::jcs};
use serde_json::json;
fn pin() -> RelayPin {
    RelayPin {
        origin: "https://relay.example.com".into(),
        chain_id: "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc".into(),
        public_key: "T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ".into(),
        log_id: "cccccccc-cccc-4ccc-8ccc-cccccccccccc".into(),
    }
}

#[test]
fn selected_history_checks_derived_admin_before_accepting_any_registry_or_head() {
    let f = jcs::parse(include_bytes!("fixtures/python-genesis-acceptance.json")).unwrap();
    let initial = initial_parameters(
        &f["parameters"],
        &f["identity"],
        f["origin"].as_str().unwrap(),
        f["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    assert_eq!(initial.sequence, 0);
    assert!(initial.devices.is_empty());
    assert!(initial.parameters.is_none()); // The advertisement has not proved a log prefix.
    let mut bad = f["parameters"].clone();
    bad["parameters"]["policy"]["allow_peer_enrollment"] = json!(false);
    assert_eq!(
        initial_parameters(
            &bad,
            &f["identity"],
            f["origin"].as_str().unwrap(),
            f["relay_public_key"].as_str().unwrap()
        )
        .err()
        .unwrap()
        .0,
        "C_SIGNATURE_INVALID"
    );
    let mut bad = f["parameters"].clone();
    bad["quota"].as_object_mut().unwrap().remove("max_bytes");
    assert!(
        initial_parameters(
            &bad,
            &f["identity"],
            f["origin"].as_str().unwrap(),
            f["relay_public_key"].as_str().unwrap()
        )
        .is_err()
    );
}

#[test]
fn full_log_pages_cannot_hide_a_control_or_claim_a_contiguous_omitted_prefix() {
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
    for response in f["log_pages"].as_array().unwrap() {
        page(response, "log", &authority.pin, 0, authority.sequence, 200).unwrap();
        for entry in response["items"].as_array().unwrap() {
            let receipt = receipts
                .iter()
                .find(|r| r["relay_seq"] == entry["relay_seq"])
                .unwrap();
            authority = authority.advance(entry, receipt).unwrap();
        }
    }
    assert_eq!(authority.sequence, 6);
    assert_eq!(
        authority.head_hash,
        f["parameters"]["head_hash"].as_str().unwrap()
    );
    let mut dropped = f["log_pages"][0].clone();
    dropped["items"].as_array_mut().unwrap().remove(1);
    assert!(page(&dropped, "log", &authority.pin, 0, 0, 200).is_err());
    let mut missing = f["log_pages"][1].clone();
    missing["items"].as_array_mut().unwrap().pop();
    assert!(page(&missing, "log", &authority.pin, 0, 3, 200).is_err());
}
#[test]
fn records_page_never_invents_a_cursor_or_uses_a_control_as_a_record() {
    let record = jcs::parse(include_bytes!("fixtures/attestations/record.json")).unwrap();
    let pin = pin();
    let valid = json!({"items":[{"relay_seq":7,"received_at_ms":1791684000000u64,"kind":"record","object":record}],"next_cursor":null,"has_more":false,"scan_through_seq":8,"log_id":pin.log_id,"generation":0,"head_seq":8,"head_hash":"9rmeVaK9mN98hMLU6FDa4ZGL77Mgrd5fwceAipags-o"});
    page(&valid, "records", &pin, 0, 6, 200).unwrap();
    for field in [
        "next_cursor",
        "head_hash",
        "generation",
        "scan_through_seq",
        "log_id",
    ] {
        let mut bad = valid.clone();
        bad.as_object_mut().unwrap().remove(field);
        assert!(page(&bad, "records", &pin, 0, 6, 200).is_err(), "{field}");
    }
    let mut bad = valid.clone();
    bad["items"][0]["relay_seq"] = json!(6);
    assert!(page(&bad, "records", &pin, 0, 6, 200).is_err());
    let mut bad = valid.clone();
    bad["items"][0]["object"]["collection_tag"] = json!("bad");
    assert!(page(&bad, "records", &pin, 0, 6, 200).is_err());
    let mut bad = valid.clone();
    bad["has_more"] = json!(true);
    assert!(page(&bad, "records", &pin, 0, 6, 200).is_err());
    let mut bad = valid.clone();
    bad["items"] = json!([]);
    page(&bad, "records", &pin, 0, 6, 200).unwrap(); // Intervening controls are only downloaded progress.
    assert!(page(&json!([]), "records", &pin, 0, 6, 200).is_err());
}
#[test]
fn rfc9457_extensions_are_discarded_and_catalogue_values_cannot_be_replaced() {
    let valid = json!({"type":"about:blank","title":"Conflict","status":409,"code":"EPOCH_STALE","retryable":true,"server_time_ms":1791684000000u64,"detail":"Fetch signed evidence.","vendor_text":"do not execute","context":{"epoch":4}});
    assert_eq!(
        problem(&valid, 409).unwrap(),
        json!({"status":409,"code":"EPOCH_STALE","retryable":true,"server_time_ms":1791684000000u64,"retry_after_ms":null})
    );
    let mut bad = valid.clone();
    bad["retryable"] = json!(false);
    assert!(problem(&bad, 409).is_err());
    assert!(problem(&valid, 410).is_err());
    let mut bad = valid.clone();
    bad["code"] = json!("NEW_ERROR");
    assert!(problem(&bad, 409).is_err());
    let mut bad = valid.clone();
    bad["context"]["secret"] = json!("not a defined hint");
    assert!(problem(&bad, 409).is_err());
}

#[test]
fn clock_and_restart_barrier_wait_survive_problem_parsing_without_authority_hints() {
    let mut value = json!({"type":"about:blank","title":"Unauthorized","status":401,"code":"TOKEN_NOT_YET_VALID","retryable":true,"server_time_ms":1791684000000u64,"context":{"retry_after_ms":120000,"epoch":2}});
    let parsed = problem(&value, 401).unwrap();
    assert_eq!(parsed["retry_after_ms"], 120000);
    assert_eq!(parsed["server_time_ms"], 1791684000000u64);
    assert!(parsed.get("context").is_none());
    value["context"]["retry_after_ms"] = json!(-1);
    assert_eq!(problem(&value, 401).unwrap_err().0, "C_SCHEMA_VIOLATION");
}

#[test]
fn signed_contradictory_heads_can_be_preserved_without_installing_their_view() {
    use pallasync_core::{attestations::TransparencyAnchor, strict::verify::*};
    let pin = pin();
    let key = ed25519_dalek::SigningKey::from_bytes(
        &(0xa0..=0xbf).collect::<Vec<_>>().try_into().unwrap(),
    );
    let mut head = json!({"type":"PALLASYNC-TRANSPARENCY-HEAD-v3","version":"3.0","chain_id":pin.chain_id,"log_id":pin.log_id,"relay_key_id":pin.key_id().unwrap(),"transparency_id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","entry_index":5,"transparency_hash":hash_bytes(&[1;32]),"head_seq":7,"head_hash":hash_bytes(&[2;32]),"parameters_hash":hash_bytes(&[3;32]),"epoch_hash":hash_bytes(&[4;32]),"min_available_seq":1,"timestamp":1});
    sign_relay(&key, &mut head, CTX_HEAD).unwrap();
    let anchor = TransparencyAnchor {
        transparency_id: head["transparency_id"].as_str().unwrap().into(),
        index: 5,
        hash: head["transparency_hash"].as_str().unwrap().into(),
    };
    let compare = |head: &serde_json::Value| {
        transparency_head(
            &json!({"head":head}),
            &pin,
            &anchor,
            7,
            &hash_bytes(&[2; 32]),
            &hash_bytes(&[3; 32]),
            &hash_bytes(&[4; 32]),
        )
    };
    compare(&head).unwrap();
    let log = json!({"log_id":pin.log_id,"generation":0,"head_seq":head["head_seq"],"head_hash":head["head_hash"],
        "min_available_seq":1,"epoch_hash":head["epoch_hash"],"parameters_hash":head["parameters_hash"]});
    compare_log_heads(&log, &head, 0).unwrap();
    let mut advanced = log.clone();
    advanced["head_seq"] = json!(8);
    assert_eq!(
        compare_log_heads(&advanced, &head, 0).unwrap_err().0,
        "C_MISSING_DEPENDENCY"
    );
    let mut fields_missing = log;
    fields_missing.as_object_mut().unwrap().remove("generation");
    assert!(log_head(&fields_missing).is_err());
    for (field, changed, code) in [
        (
            "head_hash",
            json!(hash_bytes(&[9; 32])),
            "C_LOG_HASH_MISMATCH",
        ),
        ("head_seq", json!(6), "C_ROLLBACK_DETECTED"),
        (
            "transparency_hash",
            json!(hash_bytes(&[9; 32])),
            "C_TRANSPARENCY_INVALID",
        ),
        (
            "log_id",
            json!("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
            "C_LOG_HASH_MISMATCH",
        ),
        (
            "parameters_hash",
            json!(hash_bytes(&[9; 32])),
            "C_MISSING_DEPENDENCY",
        ),
    ] {
        let mut contradiction = head.clone();
        contradiction[field] = changed;
        sign_relay(&key, &mut contradiction, CTX_HEAD).unwrap();
        assert_eq!(
            observed_transparency_head(&json!({"head":contradiction}), &pin).unwrap(),
            contradiction
        );
        assert_eq!(compare(&contradiction).unwrap_err().0, code);
    }
    let mut unsigned = head;
    unsigned["head_hash"] = json!(hash_bytes(&[9; 32]));
    assert!(observed_transparency_head(&json!({"head":unsigned}), &pin).is_err());
}
