use pallasync_core::{
    authority::Authority,
    peer_enrollment as peer, peer_response as response,
    strict::{jcs, verify::*},
};
use serde_json::{Value, json};

fn fixture() -> (Authority, Value, u64) {
    let f = jcs::parse(include_bytes!("fixtures/python-genesis-acceptance.json")).unwrap();
    let authority = Authority::from_genesis(
        &f["created"],
        &f["genesis"],
        &f["identity"],
        f["origin"].as_str().unwrap(),
        f["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    let now = f["created"]["upload_receipts"][0]["received_at_ms"]
        .as_u64()
        .unwrap();
    let device = f["genesis"]["device_record"]["device_id"].as_str().unwrap();
    let offer = peer::offer(
        &authority,
        device,
        &uuid::Uuid::new_v4().to_string(),
        &[4; 32],
        now + 300000,
        now,
    )
    .unwrap();
    (authority, offer, now)
}

#[test]
fn offer_hint_commits_the_authenticated_relay_shape_not_the_larger_qr_object() {
    let (_, offer, _) = fixture();
    let stored = json!({"chain_id":offer["chain_id"],"generation":offer["generation"],"request_id":offer["request_id"],"offer_nonce":offer["offer_nonce"],
        "offering_device_id":offer["approver_device_id"],"expires_at_ms":offer["expires_at_ms"]});
    let mut result = json!({"request_id":offer["request_id"],"expires_at_ms":offer["expires_at_ms"],"offer_hash":object_hash(&stored)});
    assert!(response::offer_created(&result, &offer).is_ok());
    result["offer_hash"] = json!(object_hash(&offer));
    assert!(response::offer_created(&result, &offer).is_err());
    let result = json!({"request_id":offer["request_id"],"status":"rejected"});
    assert!(response::rejected(&result, &offer).is_ok());
}

#[test]
fn request_hints_and_pages_verify_the_subject_signature_and_bound_hash_without_authorizing_membership()
 {
    let (authority, offer, now) = fixture();
    pallasync_core::secret_lifetime::foreground(true);
    let (subject, request) = peer::begin_subject(&offer, &authority, true, now).unwrap();
    let mut created = json!({"request_id":request["request_id"],"request_hash":object_hash(&request),"status":"awaiting_approval"});
    assert!(response::request_created(&created, &request).is_ok());
    created["request_hash"] = json!(hash_bytes(&[9; 32]));
    assert!(response::request_created(&created, &request).is_err());
    let page = json!({"items":[{"request":request,"request_hash":object_hash(&request),"status":"awaiting_approval","offering_device_id":offer["approver_device_id"]}],
        "next_cursor":null,"has_more":false,"log_id":authority.pin.log_id,"generation":0,"head_seq":authority.sequence});
    assert!(response::requests_page(&page, &authority, &offer, 200).is_ok());
    for mutation in 0..5 {
        let mut bad = page.clone();
        match mutation {
            0 => bad["items"][0]["request"]["device_id"] = json!(uuid::Uuid::new_v4().to_string()),
            1 => bad["items"][0]["offering_device_id"] = json!(uuid::Uuid::new_v4().to_string()),
            2 => bad["next_cursor"] = json!("unannounced cursor"),
            3 => bad["head_seq"] = json!(0),
            _ => bad["items"]
                .as_array_mut()
                .unwrap()
                .push(page["items"][0].clone()),
        }
        assert!(response::requests_page(&bad, &authority, &offer, 200).is_err());
    }
    assert_eq!(authority.devices.len(), 1);
    peer::end(subject);
}
