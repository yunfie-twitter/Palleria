use pallasync_core::{disclosure, strict::jcs};
use serde_json::json;
fn inputs() -> (Vec<u8>, Vec<u8>, serde_json::Value, String) {
    let md = include_bytes!("fixtures/privacy.md").to_vec();
    let bytes = include_bytes!("fixtures/privacy.json").to_vec();
    let p = jcs::parse(&bytes).unwrap();
    let key = include_str!("fixtures/relay-public-key.txt")
        .trim()
        .to_owned();
    let health = json!({"protocol":"pallasync/3","relay_origin":p["relay_origin"],"relay_public_key":key,"relay_key_id":p["relay_key_id"],"limits":p["limits"],"durability_tier":p["durability_tier"],"server_time_ms":1791684000000_u64,"shutdown_at_ms":null,"migration_origin":null});
    (md, bytes, health, key)
}
#[test]
fn real_signed_operator_pair_is_valid_and_bound_to_exact_bytes() {
    let (mut md, bytes, health, key) = inputs();
    let origin = health["relay_origin"].as_str().unwrap();
    let p = disclosure::validate_pair(
        &md,
        &bytes,
        &health,
        origin,
        &key,
        0,
        "",
        9_007_199_254_740_991,
    )
    .unwrap();
    assert_eq!(p.revision, 1);
    md.push(b'\n');
    assert_eq!(
        disclosure::validate_pair(
            &md,
            &bytes,
            &health,
            origin,
            &key,
            0,
            "",
            9_007_199_254_740_991
        )
        .err()
        .unwrap()
        .0,
        "C_RELAY_DISCLOSURE_INVALID"
    );
}
#[test]
fn key_revision_future_and_health_cannot_be_silently_reset() {
    let (md, bytes, mut health, key) = inputs();
    let origin = health["relay_origin"].as_str().unwrap().to_owned();
    for (revision, hash, now) in [
        (2, "", 9_007_199_254_740_991),
        (1, "another-hash", 9_007_199_254_740_991),
        (0, "", 0),
    ] {
        assert!(
            disclosure::validate_pair(&md, &bytes, &health, &origin, &key, revision, hash, now)
                .is_err()
        );
    }
    health["limits"]["page_limit"] = json!(3);
    assert!(
        disclosure::validate_pair(
            &md,
            &bytes,
            &health,
            &origin,
            &key,
            0,
            "",
            9_007_199_254_740_991
        )
        .is_err()
    );
    assert_eq!(
        disclosure::validate_pair(
            &md,
            &bytes,
            &health,
            &origin,
            "",
            0,
            "",
            9_007_199_254_740_991
        )
        .err()
        .unwrap()
        .0,
        "C_RELAY_KEY_MISMATCH"
    );
}
