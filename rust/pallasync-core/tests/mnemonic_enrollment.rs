use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use pallasync_core::{crypto, models::EpochKeyEnvelope};
use serde_json::Value;

const PHRASE: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
const ORIGIN: &str = "https://relay.example.com";

fn fixture() -> Vec<EpochKeyEnvelope> {
    let roots = crypto::derive_root_keys(PHRASE, "").unwrap();
    let genesis =
        crypto::create_genesis_bundle(PHRASE, "", "Original", "os-keystore", ORIGIN).unwrap();
    let genesis: Value = serde_json::from_str(&genesis.genesis_request_body_json).unwrap();
    let mut envelopes: Vec<EpochKeyEnvelope> = serde_json::from_value(Value::Array(
        genesis["envelopes"]
            .as_array()
            .unwrap()
            .iter()
            .filter(|env| env["recipient_kind"] == "recovery")
            .cloned()
            .collect(),
    ))
    .unwrap();

    let secret = crypto::generate_epoch_secret();
    let keys = crypto::derive_epoch_keys(&roots.chain_id_digest, 1, &secret).unwrap();
    envelopes.push(
        crypto::wrap_recovery_epoch_envelope(
            &uuid::Uuid::new_v4().to_string(),
            &roots.chain_id,
            0,
            1,
            Some(&URL_SAFE_NO_PAD.encode([7u8; 32])),
            &keys.epoch_commitment_b64u,
            "admin",
            None,
            &roots.admin_key,
            &roots.recovery_kek,
            &secret,
            None,
        )
        .unwrap(),
    );
    envelopes
}

#[test]
fn mnemonic_enrollment_creates_a_device_envelope_for_each_eligible_epoch() {
    let envelopes = fixture();
    let envelope_json = serde_json::to_string(&envelopes).unwrap();
    let bundle = crypto::create_mnemonic_enrollment_bundle(
        PHRASE,
        "",
        "Restored device",
        "os-keystore",
        0,
        1,
        &envelope_json,
        &URL_SAFE_NO_PAD.encode([8u8; 32]),
        &URL_SAFE_NO_PAD.encode([9u8; 32]),
        ORIGIN,
    )
    .unwrap();
    let body: Value = serde_json::from_str(&bundle.enroll_request_body_json).unwrap();
    let epochs: Vec<u64> = body["envelopes"]
        .as_array()
        .unwrap()
        .iter()
        .map(|envelope| envelope["epoch"].as_u64().unwrap())
        .collect();
    assert_eq!(epochs, [0, 1]);
    assert!(
        body["envelopes"]
            .as_array()
            .unwrap()
            .iter()
            .all(|envelope| {
                envelope["recipient_kind"] == "device"
                    && envelope["recipient_device_id"] == bundle.device_id
                    && envelope["signer_kind"] == "admin"
            })
    );
}

#[test]
fn mnemonic_enrollment_rejects_missing_current_or_unsorted_duplicate_epochs() {
    for epochs in [vec![0], vec![1, 0], vec![0, 0, 1]] {
        let all = fixture();
        let selected: Vec<_> = epochs
            .iter()
            .map(|epoch| {
                all.iter()
                    .find(|envelope| envelope.epoch == *epoch)
                    .unwrap()
                    .clone()
            })
            .collect();
        let result = crypto::create_mnemonic_enrollment_bundle(
            PHRASE,
            "",
            "Restored device",
            "os-keystore",
            0,
            1,
            &serde_json::to_string(&selected).unwrap(),
            &URL_SAFE_NO_PAD.encode([8u8; 32]),
            &URL_SAFE_NO_PAD.encode([9u8; 32]),
            ORIGIN,
        );
        assert!(result.is_err(), "epochs {epochs:?} must fail closed");
    }
}
