use pallasync_core::{
    models::{InnerRecord, SyncRecord},
    schema,
    strict::{jcs, verify},
};
use serde_json::{Value, json};

fn inner() -> Value {
    json!({"collection":"palleria.search_history/3","device_seq":1,"prev_record_hash":null,"operations":[{"entity_id":"query","operation":"upsert","lamport":1,"created_at_ms":0,"context":{},"body":{"query":"query"}}]})
}

#[test]
fn recovery_set_and_ceremony_audits_require_null_holder_kind() {
    let mut value = inner();
    value["collection"] = json!("pallasync.audit/3");
    value["operations"][0]["entity_id"] = json!("11111111-1111-4111-8111-111111111111");
    value["operations"][0]["body"] = json!({
        "event":"recovery_share_set_created","subject_id":null,"object_hash":null,"result":"succeeded",
        "ext":{"org.palleria.recovery":{"recovery_set_id":"22222222-2222-4222-8222-222222222222",
        "recovery_secret_fingerprint":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","threshold":2,"total_shares":3,
        "holder_kind":null,"holder_counts":{"device":2,"offline":1}}}
    });
    for event in [
        "recovery_share_set_created",
        "recovery_share_set_rotated",
        "recovery_ceremony_started",
        "recovery_ceremony_succeeded",
        "recovery_ceremony_failed",
    ] {
        value["operations"][0]["body"]["event"] = json!(event);
        for kind in [Value::Null, json!("device"), json!("offline")] {
            value["operations"][0]["body"]["ext"]["org.palleria.recovery"]["holder_kind"] =
                kind.clone();
            assert_eq!(schema::validate_inner(&value).is_ok(), kind.is_null());
        }
    }
}

#[test]
fn lexical_negatives_are_rejected_before_conversion() {
    for bytes in [
        r#"{"a":1,"a":2}"#,
        r#"{"a":1e3}"#,
        r#"{"a":1.0}"#,
        r#"{"a":-0}"#,
        r#"{"a":01}"#,
        r#"{"a":9007199254740992}"#,
        r#"{"a":"\ud800"}"#,
    ] {
        assert!(jcs::parse(bytes.as_bytes()).is_err(), "{bytes}");
    }
    assert!(jcs::parse(&[239, 187, 191, b'{', b'}']).is_err());
    assert!(jcs::parse(&[b'"', 255, b'"']).is_err());
    assert!(jcs::parse(format!("{}0{}", "[".repeat(33), "]".repeat(33)).as_bytes()).is_err());
}

#[test]
fn utf16_sorting_and_exact_escape_profile() {
    let v = json!({"\u{e000}":1,"\u{1f600}":2});
    assert_eq!(
        String::from_utf8(jcs::canonicalize_json(&v).unwrap()).unwrap(),
        "{\"😀\":2,\"\":1}"
    );
    assert_eq!(
        jcs::canonicalize_json(&json!("/\u{0}\u{8}\n\t")).unwrap(),
        b"\"/\\u0000\\b\\n\\t\""
    );
}

#[test]
fn nullable_members_are_required_and_context_body_are_not_defaults() {
    let mut v = inner();
    assert!(schema::validate_inner(&v).is_ok());
    v.as_object_mut().unwrap().remove("prev_record_hash");
    assert!(serde_json::from_value::<InnerRecord>(v).is_err());
    for key in ["context", "body"] {
        let mut v = inner();
        v["operations"][0].as_object_mut().unwrap().remove(key);
        assert!(schema::validate_inner(&v).is_err());
    }
    let v = json!({"version":"3.0","chain_id":"x","generation":0,"record_id":"x","device_id":"x","epoch":0,"payload_nonce":"x","encrypted_payload":"x","signature":"x"});
    assert!(serde_json::from_value::<SyncRecord>(v).is_err());
}

#[test]
fn plaintext_validation_is_atomic_and_preserves_signed_identifiers() {
    for mutate in 0..5 {
        let mut v = inner();
        match mutate {
            0 => v["operations"][0]["body"]["query"] = json!("different"),
            1 => v["operations"][0]["context"]["unknown"] = json!(0),
            2 => v["operations"][0]["entity_id"] = json!("e\u{301}"),
            3 => v["device_seq"] = json!(2),
            _ => v["operations"][0]["body"]["extra"] = json!(true),
        }
        assert!(schema::validate_inner(&v).is_err());
    }
    let mut v = inner();
    v["collection"] = json!("example.unknown/3");
    assert_eq!(
        schema::validate_inner(&v).unwrap_err().0,
        "C_UNSUPPORTED_SCHEMA"
    );
}

#[test]
fn protected_artifacts_cannot_be_smuggled_through_normal_extensions() {
    for kind in [
        "PALLASYNC-RECOVERY-BLOB-v3",
        "PALLASYNC-RECOVERY-SET-v3",
        "PALLASYNC-RECOVERY-STATUS-v3",
        "PALLASYNC-RECOVERY-SHARE-v3",
        "PALLASYNC-RECOVERY-PACKAGE-v3",
        "PALLASYNC-OFFLINE-SHARE-KEY-v3",
        "PALLASYNC-RECOVERY-SESSION-v3",
        "PALLASYNC-RECOVERY-TRANSFER-v3",
    ] {
        let mut value = inner();
        value["ext"] = json!({"org.palleria.fixture":{"nested":[{"type":kind}]}});
        assert_eq!(
            schema::validate_inner(&value).unwrap_err().0,
            "C_SCHEMA_VIOLATION"
        );
        assert!(
            pallasync_core::device_session::prepare_frame(&jcs::canonicalize_json(&value).unwrap())
                .is_err()
        );
    }
    let mut value = inner();
    value["ext"] =
        json!({"org.palleria.fixture":{"mnemonic":"public fixture only","passphrase":""}});
    assert!(schema::validate_inner(&value).is_err());
    value["ext"] =
        json!({"org.palleria.fixture":{"text":"PSR3:debug","type":"example","nested":[1,null]}});
    assert!(schema::validate_inner(&value).is_ok());
    let package =
        pallasync_core::recovery::portable(&json!({"type":"PALLASYNC-RECOVERY-PACKAGE-v3"}))
            .unwrap();
    value["ext"] = json!({"org.palleria.fixture":{"text":package}});
    assert!(schema::validate_inner(&value).is_err());
}

#[test]
fn canonical_binary_and_prime_order_points_only() {
    assert!(verify::decode("AB", Some(1)).is_err());
    assert!(verify::decode("AA=", Some(1)).is_err());
    assert!(!verify::prime_point(&[0; 32]));
    let mut identity = [0; 32];
    identity[0] = 1;
    assert!(!verify::prime_point(&identity));
    let mut p = [255; 32];
    p[0] = 237;
    p[31] = 127;
    use base64::Engine;
    assert!(!verify::valid_x_key(
        &base64::engine::general_purpose::URL_SAFE_NO_PAD.encode(p)
    ));
}
