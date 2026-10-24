use pallasync_core::{
    authority::Authority,
    root_session::{self, Operation},
};
use serde_json::Value;

const MNEMONIC: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";

fn authority() -> (Value, Authority) {
    let fixture: Value =
        serde_json::from_slice(include_bytes!("fixtures/python-genesis-acceptance.json")).unwrap();
    let authority = Authority::from_genesis(
        &fixture["created"],
        &fixture["genesis"],
        &fixture["identity"],
        fixture["origin"].as_str().unwrap(),
        fixture["relay_public_key"].as_str().unwrap(),
    )
    .unwrap();
    (fixture, authority)
}

#[test]
fn prepares_fresh_generation_device_epoch_and_both_envelopes() {
    let (fixture, authority) = authority();
    let handle = root_session::begin(
        MNEMONIC.as_bytes(),
        b"",
        Operation::Reseed,
        Some(fixture["identity"]["chain_id"].as_str().unwrap()),
        Some(fixture["identity"]["admin_public_key"].as_str().unwrap()),
    )
    .unwrap();
    let prepared =
        pallasync_core::reseed::prepare(handle, &authority, "re-seeded device", 1_800_000_000_000)
            .unwrap();
    let public = prepared.public().clone();
    let old_device = fixture["genesis"]["device_record"]["device_id"]
        .as_str()
        .unwrap();
    assert_eq!(public["generation"], 1);
    assert_eq!(public["parameters"]["generation"], 1);
    assert_eq!(public["epoch"]["generation"], 1);
    assert_eq!(public["epoch"]["reason"], "reseed");
    assert!(
        public["epoch"]["revoked_device_ids"]
            .as_array()
            .unwrap()
            .iter()
            .any(|id| id == old_device)
    );
    assert_eq!(
        public["epoch"]["members"][0],
        public["device_record"]["device_id"]
    );
    assert_ne!(public["device_record"]["device_id"], old_device);
    assert_eq!(public["envelopes"].as_array().unwrap().len(), 2);
    assert_eq!(public["envelopes"][0]["recipient_kind"], "device");
    assert_eq!(public["envelopes"][1]["recipient_kind"], "recovery");
    assert!(public.get("device_signing_seed").is_none());
    assert!(public.get("device_kex_private").is_none());
    root_session::end(handle);
}

#[test]
fn rejects_reseed_preparation_from_an_unrelated_root_operation() {
    let (fixture, authority) = authority();
    let handle = root_session::begin(
        MNEMONIC.as_bytes(),
        b"",
        Operation::Delete,
        Some(fixture["identity"]["chain_id"].as_str().unwrap()),
        Some(fixture["identity"]["admin_public_key"].as_str().unwrap()),
    )
    .unwrap();
    let error = match pallasync_core::reseed::prepare(
        handle,
        &authority,
        "re-seeded device",
        1_800_000_000_000,
    ) {
        Ok(_) => panic!("non-reseed operation created reseed material"),
        Err(error) => error,
    };
    assert_eq!(error.0, "C_RECOVERY_AUTH_REQUIRED");
    root_session::end(handle);
}
