use pallasync_core::legacy_migration::{
    convert_v21_data_payload, convert_v21_data_payload_bound, convert_v21_payload,
    convert_v21_snapshot_payload,
};
use serde_json::json;

fn payload(schema: &str, entity_id: &str, operation: &str, body: serde_json::Value) -> Vec<u8> {
    serde_json::to_vec(&json!({
        "schema": schema,
        "entity_id": entity_id,
        "operation": operation,
        "context": {},
        "lamport": 17,
        "created_at_ms": 1_700_000_000_000_i64,
        "body": body,
    }))
    .unwrap()
}

#[test]
fn converts_v21_entity_payloads_without_reusing_legacy_stamps() {
    let candidate = convert_v21_payload(
        &payload(
            "palleria.favorite_tag/2",
            "e\u{301}",
            "upsert",
            json!({"tag":"e\u{301}"}),
        ),
        "palleria.favorite_tag/2",
        "upsert",
    )
    .unwrap();
    assert_eq!(candidate.collection, "palleria.favorite_tag/3");
    assert_eq!(candidate.legacy_entity_id, "e\u{301}");
    assert_eq!(candidate.entity_id, "é");
    assert_eq!(candidate.body, json!({"tag":"é"}));
    assert!(candidate.normalization_changed);
    assert_eq!(candidate.legacy_lamport, 17); // evidence only; destination Lamport is unassigned

    let seen = convert_v21_payload(
        &payload(
            "palleria.view_history/2",
            "seen:123",
            "upsert",
            json!({"id":123}),
        ),
        "palleria.view_history/2",
        "upsert",
    )
    .unwrap();
    assert_eq!(seen.collection, "palleria.view_history/3");
    assert_eq!(seen.entity_id, "seen:123");

    let viewed = convert_v21_payload(
        &payload(
            "palleria.view_history/2",
            "viewed:123",
            "upsert",
            json!({"id":123,"tags":["e\u{301}","é"]}),
        ),
        "palleria.view_history/2",
        "upsert",
    )
    .unwrap();
    assert_eq!(viewed.body["tags"], json!(["é"]));
    assert!(viewed.normalization_changed);
    assert!(viewed.normalization_collision);
}

#[test]
fn rejects_payload_envelope_mismatch_unknown_fields_and_noncanonical_ids() {
    let mismatch = convert_v21_payload(
        &payload(
            "palleria.search_history/2",
            "query",
            "upsert",
            json!({"query":"query"}),
        ),
        "palleria.favorite_tag/2",
        "upsert",
    );
    assert!(mismatch.is_err());

    let mut unknown: serde_json::Value = serde_json::from_slice(&payload(
        "palleria.search_history/2",
        "query",
        "upsert",
        json!({"query":"query"}),
    ))
    .unwrap();
    unknown["unrecognized"] = json!(true);
    assert!(
        convert_v21_payload(
            &serde_json::to_vec(&unknown).unwrap(),
            "palleria.search_history/2",
            "upsert"
        )
        .is_err()
    );

    assert!(
        convert_v21_payload(
            &payload("palleria.view_history/2", "seen:01", "delete", json!({})),
            "palleria.view_history/2",
            "delete",
        )
        .is_err()
    );
}

#[test]
fn v21_envelope_and_inner_payload_stamps_must_agree() {
    let plaintext = serde_json::to_vec(&json!({
        "schema":"palleria.search_history/2",
        "entity_id":"q",
        "operation":"upsert",
        "context":{},
        "lamport":7,
        "created_at_ms":1_700_000_000_007_i64,
        "body":{"query":"q"}
    }))
    .unwrap();

    assert!(
        convert_v21_data_payload_bound(
            &plaintext,
            "palleria.search_history/2",
            "upsert",
            Some(7),
            Some(1_700_000_000_007),
        )
        .is_ok()
    );
    assert!(
        convert_v21_data_payload_bound(
            &plaintext,
            "palleria.search_history/2",
            "upsert",
            Some(8),
            Some(1_700_000_000_007),
        )
        .is_err()
    );
    assert!(
        convert_v21_data_payload_bound(
            &plaintext,
            "palleria.search_history/2",
            "upsert",
            Some(7),
            Some(1_700_000_000_008),
        )
        .is_err()
    );
}

#[test]
fn converts_v21_replacement_snapshots_without_fabricating_deletes() {
    let favorites = convert_v21_snapshot_payload(
        "palleria.favorite_tag/1",
        &json!(["e\u{301}", "é", "landscape"]),
    )
    .unwrap();
    assert_eq!(favorites.len(), 2);
    assert_eq!(favorites[0].entity_id, "é");
    assert!(favorites[0].normalization_collision);
    assert_eq!(favorites[1].entity_id, "landscape");
    assert!(
        favorites
            .iter()
            .all(|candidate| candidate.operation == "upsert")
    );

    let search =
        convert_v21_snapshot_payload("palleria.search_history/1", &json!(["newest", "older"]))
            .unwrap();
    assert_eq!(
        search
            .iter()
            .map(|candidate| candidate.entity_id.as_str())
            .collect::<Vec<_>>(),
        ["older", "newest"]
    );

    let viewed = convert_v21_snapshot_payload(
        "palleria.view_history/1",
        &json!({
            "seenFeedIllusts":["42"],
            "viewedIllusts":[
                {"id":"42","pageCount":"2","title":"e\u{301}","tags":["e\u{301}","é"]},
                {"id":7,"pageCount":1,"title":"newer"}
            ]
        }),
    )
    .unwrap();
    assert_eq!(viewed.len(), 3);
    assert_eq!(viewed[0].entity_id, "seen:42");
    assert_eq!(viewed[1].entity_id, "viewed:7");
    assert_eq!(viewed[2].entity_id, "viewed:42");
    assert_eq!(viewed[2].body["id"], 42);
    assert_eq!(viewed[2].body["pageCount"], 2);
    assert_eq!(viewed[2].body["title"], "é");
    assert_eq!(viewed[2].body["tags"], json!(["é"]));
    assert!(viewed[2].normalization_changed && viewed[2].normalization_collision);

    assert!(
        convert_v21_snapshot_payload(
            "palleria.view_history/1",
            &json!({"seenFeedIllusts":["01"],"viewedIllusts":[]}),
        )
        .is_err()
    );

    let wire_snapshot = payload(
        "palleria.search_history/1",
        "snapshot",
        "upsert",
        json!(["newest", "older"]),
    );
    let routed =
        convert_v21_data_payload(&wire_snapshot, "palleria.search_history/1", "upsert").unwrap();
    assert_eq!(routed.len(), 2);
    assert_eq!(routed[0].entity_id, "older");
    assert!(
        convert_v21_data_payload(&wire_snapshot, "palleria.favorite_tag/1", "upsert",).is_err()
    );
}
