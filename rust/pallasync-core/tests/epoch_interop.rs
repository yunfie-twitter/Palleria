//! Actual independent Python -> isolated relay controls, not fabricated acceptances.
use pallasync_core::{bootstrap, client_response, strict::jcs};
use serde_json::{Value, json};
use std::collections::BTreeMap;

#[test]
fn independent_epoch_receipts_bind_the_complete_original_proposal() {
    let f = jcs::parse(include_bytes!("fixtures/python-epoch-acceptance.json")).unwrap();
    let authority = client_response::initial_parameters(
        &f["parameters"],
        &f["identity"],
        f["origin"].as_str().unwrap(),
        f["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    let e = &f["epoch_checks"];
    for (accepted, body) in [
        (&e["manual_accepted"], &e["manual_body"]),
        (&e["revocation_accepted"], &e["revocation_body"]),
    ] {
        let validate = |response: &Value, submitted: &Value| {
            if let Some(device) = accepted["device_id"].as_str() {
                bootstrap::revocation_acceptance(response, submitted, &authority.pin, device)
            } else {
                bootstrap::epoch_acceptance(response, submitted, &authority.pin)
            }
        };
        validate(accepted, body).unwrap();
        let mut missing = accepted.clone();
        missing["upload_receipts"].as_array_mut().unwrap().remove(0);
        assert_eq!(
            validate(&missing, body).unwrap_err().0,
            "C_ACCEPTANCE_UNPROVEN"
        );
        let mut wrong = body.clone();
        wrong["envelopes"][0]["envelope_id"] = json!(uuid::Uuid::new_v4().to_string());
        assert_eq!(
            validate(accepted, &wrong).unwrap_err().0,
            "C_ACCEPTANCE_UNPROVEN"
        );
    }
}

#[test]
fn independent_complete_epoch_history_requires_admin_cas_and_exact_original_payload() {
    let f = jcs::parse(include_bytes!("fixtures/python-epoch-acceptance.json")).unwrap();
    let epochs = &f["epoch_checks"];
    let mut authority = client_response::initial_parameters(
        &f["parameters"],
        &f["identity"],
        f["origin"].as_str().unwrap(),
        f["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    let mut bound = authority.with_action_payload(&epochs["revocation_body"]["admin_operation"], &json!({"epoch":epochs["revocation_body"]["epoch"],"envelopes":epochs["revocation_body"]["envelopes"]})).unwrap();
    let mut receipts = BTreeMap::new();
    for response in [
        &f["created"],
        &f["record_upload"],
        &f["enrollment"]["accepted"],
        &epochs["manual_accepted"],
        &epochs["record_upload"],
        &epochs["revocation_accepted"],
    ] {
        for receipt in response["upload_receipts"].as_array().unwrap() {
            assert!(
                receipts
                    .insert(receipt["relay_seq"].as_u64().unwrap(), receipt)
                    .is_none()
            );
        }
    }
    let mut accepted_old: Option<Value> = None;
    for pages in [
        &f["log_pages"],
        &f["enrollment"]["log_pages"],
        &epochs["log_pages"],
    ] {
        for page in pages.as_array().unwrap() {
            for entry in page["items"].as_array().unwrap() {
                let receipt = receipts[&entry["relay_seq"].as_u64().unwrap()];
                bound = bound.advance(entry, receipt).unwrap();
                if entry["relay_seq"] == 18 {
                    assert_eq!(
                        authority.advance(entry, receipt).err().unwrap().0,
                        "C_MISSING_DEPENDENCY"
                    );
                    assert_eq!(authority.current_epoch().unwrap().record["epoch"], 1);
                    assert!(authority.devices.values().all(|d| d.revoked_seq.is_none()));
                } else {
                    authority = authority.advance(entry, receipt).unwrap();
                }
                if entry["relay_seq"] == 6 {
                    accepted_old = Some(entry["object"].clone());
                }
            }
        }
    }
    bound.complete_cut().unwrap();
    assert_eq!(bound.sequence, 18);
    assert_eq!(bound.current_epoch().unwrap().record["epoch"], 2);
    bootstrap::history_cut(&bound, &json!({"head":epochs["transparency_head"]})).unwrap();
    bound.record_eligible(&accepted_old.unwrap(), 6).unwrap();
    bound.record_eligible(&epochs["record"], 14).unwrap();
    assert_eq!(
        bound.record_eligible(&epochs["record"], 19).unwrap_err().0,
        "C_ACCEPTANCE_UNPROVEN"
    );
}
