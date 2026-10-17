//! Native factory/lifetime tests with synthetic signed relay evidence, not a real approval claim.
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::{Signer, SigningKey};
use pallasync_core::{
    attestations::{self, RelayPin},
    authority::Authority,
    crypto, peer_enrollment as peer,
    strict::{jcs, verify::*},
};
use serde_json::{Value, json};
use zeroize::Zeroizing;

const PHRASE: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
const NOW: u64 = 1_791_684_000_000;
fn apply(state: Authority, kind: &str, object: Value) -> Authority {
    let mut entry =
        json!({"relay_seq":state.sequence+1,"received_at_ms":NOW,"kind":kind,"object":object});
    if kind == "epoch" {
        entry["committed_at_ms"] = json!(NOW);
    }
    let relay = SigningKey::from_bytes(&std::array::from_fn(|i| 0xa0 + i as u8));
    let mut receipt = json!({"type":"PALLASYNC-UPLOAD-RECEIPT-v3","version":"3.0","receipt_id":uuid::Uuid::new_v4().to_string(),
        "chain_id":state.pin.chain_id,"log_id":state.pin.log_id,"relay_key_id":state.pin.key_id().unwrap(),"relay_seq":state.sequence+1,
        "object_hash":object_hash(&entry["object"]),"entry_hash":object_hash(&entry),"previous_head_hash":state.head_hash,
        "resulting_head_hash":attestations::data_link(&state.head_hash,&entry).unwrap(),"received_at_ms":NOW,
        "transparency_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd","transparency_index":state.sequence+1,"transparency_hash":hash_bytes(&[1;32])});
    receipt["relay_signature"] = json!(
        URL_SAFE_NO_PAD.encode(
            relay
                .sign(&signing_bytes(&receipt, CTX_RECEIPT, "relay_signature"))
                .to_bytes()
        )
    );
    state.advance(&entry, &receipt).unwrap()
}
fn fixture() -> (Authority, crypto::GenesisBundle, Value) {
    pallasync_core::secret_lifetime::foreground(true);
    let bundle = crypto::create_genesis_bundle(
        PHRASE,
        "",
        "Factory fixture",
        "os-keystore",
        "https://relay.example.com",
    )
    .unwrap();
    let genesis = jcs::parse(bundle.genesis_request_body_json.as_bytes()).unwrap();
    let relay = SigningKey::from_bytes(&std::array::from_fn(|i| 0xa0 + i as u8));
    let pin = RelayPin {
        origin: "https://relay.example.com".into(),
        chain_id: bundle.chain_id.clone(),
        public_key: URL_SAFE_NO_PAD.encode(relay.verifying_key().as_bytes()),
        log_id: uuid::Uuid::new_v4().to_string(),
    };
    let mut authority = Authority::new(
        pin,
        genesis["parameters"]["admin_public_key"]
            .as_str()
            .unwrap()
            .into(),
    )
    .unwrap();
    authority = apply(authority, "parameters", genesis["parameters"].clone());
    authority = apply(authority, "device", genesis["device_record"].clone());
    for envelope in genesis["envelopes"].as_array().unwrap() {
        authority = apply(authority, "envelope", envelope.clone());
    }
    authority = apply(authority, "epoch", genesis["epoch"].clone());
    authority.complete_cut().unwrap();
    let offer = peer::offer(
        &authority,
        &bundle.device_id,
        &uuid::Uuid::new_v4().to_string(),
        &[2; 32],
        NOW + 300_000,
        NOW,
    )
    .unwrap();
    (authority, bundle, offer)
}
fn approver(authority: &Authority, bundle: &crypto::GenesisBundle, offer: &Value) -> u64 {
    let signing = Zeroizing::new(decode(&bundle.device_signing_key, Some(32)).unwrap());
    let kex = Zeroizing::new(decode(&bundle.device_kex_private_key, Some(32)).unwrap());
    peer::begin_approver(offer, authority, &signing, &kex, NOW).unwrap()
}

#[test]
fn both_endpoints_require_local_sas_confirmation_and_bind_the_same_subject_certificate() {
    let (authority, bundle, offer) = fixture();
    assert!(peer::begin_subject(&offer, &authority, false, NOW).is_err());
    let (subject, request) = peer::begin_subject(&offer, &authority, true, NOW).unwrap();
    let approving = approver(&authority, &bundle, &offer);
    assert_ne!(request["device_id"], bundle.device_id);
    assert!(
        verify_signed_json(
            s(&request, "device_public_key"),
            s(&request, "signature"),
            &request,
            CTX_REQUEST
        )
        .unwrap()
    );
    let sas = peer::visual_sas(subject, NOW).unwrap();
    assert_eq!(sas.len(), 12);
    assert!(
        sas.bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    );
    assert_eq!(sas, peer::bind_request(approving, &request, NOW).unwrap());
    assert_eq!(sas, peer::bind_request(approving, &request, NOW).unwrap());
    peer::confirm(approving, &sas, true, NOW).unwrap();
    let certificate = peer::certificate(approving, NOW).unwrap();
    assert_eq!(certificate, peer::certificate(approving, NOW + 1).unwrap());
    // The subject has not confirmed. Receiving an otherwise valid certificate cannot bypass it.
    assert!(peer::accept_certificate(subject, &certificate, NOW).is_err());
    assert!(peer::visual_sas(subject, NOW).is_err());
    let fresh_offer = peer::offer(
        &authority,
        &bundle.device_id,
        &uuid::Uuid::new_v4().to_string(),
        &[3; 32],
        NOW + 300_000,
        NOW,
    )
    .unwrap();
    let (new_subject, new_request) =
        peer::begin_subject(&fresh_offer, &authority, true, NOW).unwrap();
    let new_approving = approver(&authority, &bundle, &fresh_offer);
    let new_sas = peer::bind_request(new_approving, &new_request, NOW).unwrap();
    assert_eq!(new_sas, peer::visual_sas(new_subject, NOW).unwrap());
    peer::confirm(new_subject, &new_sas, true, NOW).unwrap();
    peer::confirm(new_approving, &new_sas, true, NOW).unwrap();
    let cert = peer::certificate(new_approving, NOW).unwrap();
    peer::accept_certificate(new_subject, &cert, NOW).unwrap();
    peer::accept_certificate(new_subject, &cert, NOW + 1).unwrap();
    assert_eq!(cert["request_hash"], object_hash(&new_request));
    assert!(
        verify_signed_json(
            &bundle.device_public_key,
            s(&cert, "signature"),
            &cert,
            CTX_ENROLLMENT
        )
        .unwrap()
    );
    let secret = Zeroizing::new(decode(&bundle.epoch_secret, Some(32)).unwrap());
    let envelope = peer::envelope(new_approving, &authority, 0, &secret, NOW).unwrap();
    peer::validate_subject_envelopes(
        new_subject,
        &authority,
        &cert,
        std::slice::from_ref(&envelope),
        NOW,
    )
    .unwrap();
    assert_eq!(
        peer::unwrap_subject_envelope(new_subject, &authority, &cert, &envelope, NOW)
            .unwrap()
            .as_slice(),
        secret.as_slice()
    );
    assert_eq!(
        envelope,
        peer::envelope(new_approving, &authority, 0, &secret, NOW + 1).unwrap()
    );
    let prepared = peer::prepare_subject(
        new_subject,
        &authority,
        &envelope,
        "Paired fixture",
        "os-keystore",
        NOW,
    )
    .unwrap();
    assert_eq!(prepared.epoch_secret().as_slice(), secret.as_slice());
    let body = jcs::parse(
        prepared.public["request_body_json"]
            .as_str()
            .unwrap()
            .as_bytes(),
    )
    .unwrap();
    assert!(body["admin_operation"].is_null());
    assert_eq!(body["device_record"]["enrollment"], cert);
    assert!(
        verify_signed_json(
            s(&new_request, "device_public_key"),
            s(&body["device_record"], "signature"),
            &body["device_record"],
            CTX_DEVICE_RECORD
        )
        .unwrap()
    );
    let metadata_key: [u8; 32] = decode(&bundle.device_meta_key, Some(32))
        .unwrap()
        .try_into()
        .unwrap();
    let record = serde_json::from_value(body["device_record"].clone()).unwrap();
    assert_eq!(
        crypto::decrypt_device_meta(&record, &metadata_key)
            .unwrap()
            .name,
        "Paired fixture"
    );
    let retried = peer::prepare_subject(
        new_subject,
        &authority,
        &envelope,
        "Paired fixture",
        "os-keystore",
        NOW + 1,
    )
    .unwrap();
    assert_eq!(prepared.public, retried.public);
    assert_eq!(
        peer::approval_candidate(new_approving, &authority, &body, NOW).unwrap(),
        body
    );
    let mut changed = body.clone();
    changed["envelopes"][0]["ciphertext"] = json!(URL_SAFE_NO_PAD.encode([3; 48]));
    assert!(peer::approval_candidate(new_approving, &authority, &changed, NOW).is_err());
    assert!(peer::validate_subject_envelopes(new_subject, &authority, &cert, &[], NOW).is_err());
    assert!(peer::visual_sas(new_approving, NOW).is_err());
    peer::end(approving);
    peer::end(new_approving);
    peer::end(new_subject);
}

fn s<'a>(value: &'a Value, key: &str) -> &'a str {
    value[key].as_str().unwrap()
}

#[test]
fn changed_offer_request_mismatch_cancel_and_expiry_cannot_reuse_the_session() {
    let (authority, bundle, offer) = fixture();
    for member in [
        "parameters_hash",
        "epoch_hash",
        "approver_device_record_hash",
        "admin_public_key",
    ] {
        let mut changed = offer.clone();
        changed[member] = json!(hash_bytes(&[9; 32]));
        assert!(peer::begin_subject(&changed, &authority, true, NOW).is_err());
    }
    let mut extra = offer.clone();
    extra["unknown"] = json!(true);
    assert!(peer::begin_subject(&extra, &authority, true, NOW).is_err());
    let (subject, request) = peer::begin_subject(&offer, &authority, true, NOW).unwrap();
    let approving = approver(&authority, &bundle, &offer);
    peer::bind_request(approving, &request, NOW).unwrap();
    let (different_subject, different) =
        peer::begin_subject(&offer, &authority, true, NOW).unwrap();
    assert!(peer::bind_request(approving, &different, NOW).is_err());
    assert!(peer::bind_request(approving, &request, NOW).is_err());
    assert!(peer::confirm(subject, "000000000000", false, NOW).is_err());
    assert!(peer::visual_sas(subject, NOW).is_err());
    let (expired, _) = peer::begin_subject(&offer, &authority, true, NOW).unwrap();
    assert!(peer::visual_sas(expired, NOW + 300_000).is_err());
    let cancelled = approver(&authority, &bundle, &offer);
    peer::end(cancelled);
    assert!(peer::bind_request(cancelled, &request, NOW).is_err());
    peer::end(different_subject);
}
