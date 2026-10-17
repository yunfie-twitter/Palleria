//! Whole-record validation of §7.3 and every §8 collection. No partial apply.
use crate::{
    client_error::{ClientError, Result},
    public_wire::{fields, id, n, s, uuid},
    strict::{jcs, verify::decode},
};
use serde_json::Value;
use std::collections::BTreeSet;

fn invalid() -> ClientError {
    ClientError("C_SCHEMA_VIOLATION")
}
fn check(ok: bool) -> Result {
    if ok { Ok(()) } else { Err(invalid()) }
}
fn text(v: &Value, lo: usize, hi: usize, identifier: bool) -> bool {
    v.as_str()
        .is_some_and(|s| (lo..=hi).contains(&s.len()) && (!identifier || id(s)))
}
pub fn extension(v: &Value) -> Result {
    let o = v.as_object().ok_or_else(invalid)?;
    check(jcs::canonicalize_json(v).map_err(|_| invalid())?.len() <= 1024)?;
    for k in o.keys() {
        let labels: Vec<_> = k.split('.').collect();
        check(
            k.len() <= 255
                && labels.len() >= 3
                && labels.iter().enumerate().all(|(i, l)| {
                    let last = i + 1 == labels.len();
                    !l.is_empty()
                        && l.len() <= if last { 64 } else { 63 }
                        && l.as_bytes()[0].is_ascii_lowercase()
                        && !l.ends_with('-')
                        && l.bytes().all(|b| {
                            b.is_ascii_lowercase()
                                || b.is_ascii_digit()
                                || if last { b == b'_' } else { b == b'-' }
                        })
                }),
        )?;
    }
    Ok(())
}
fn ext(v: &Value) -> Result {
    if let Some(e) = v.get("ext") {
        extension(e)?;
    }
    Ok(())
}
fn empty(v: &Value) -> bool {
    v.as_object().is_some_and(|o| o.is_empty())
}
fn decimal(s: &str) -> bool {
    !s.is_empty()
        && s.len() <= 32
        && s.as_bytes()[0] != b'0'
        && s.bytes().all(|b| b.is_ascii_digit())
}
pub fn valid_order_key(v: &str) -> bool {
    (1..=256).contains(&v.len())
        && !v.ends_with('0')
        && v.bytes()
            .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
}
pub fn validate_inner(v: &Value) -> Result {
    crate::normal_data_boundary::check(v)?;
    fields(
        v,
        &[
            ("collection", "string"),
            ("device_seq", "int"),
            ("prev_record_hash", "b32?"),
            ("operations", "array"),
        ],
        &[("ext", "object")],
    )?;
    ext(v)?;
    check(
        text(&v["collection"], 1, 128, true)
            && n(v, "device_seq") >= 1
            && (n(v, "device_seq") == 1) == v["prev_record_hash"].is_null(),
    )?;
    let operations = v["operations"].as_array().ok_or_else(invalid)?;
    check((1..=1024).contains(&operations.len()))?;
    let collection = s(v, "collection");
    let mut previous = None;
    for op in operations {
        fields(
            op,
            &[
                ("entity_id", "string"),
                ("operation", "upsert|delete|clear"),
                ("lamport", "int"),
                ("created_at_ms", "int"),
                ("context", "object"),
                ("body", "object"),
            ],
            &[("ext", "object")],
        )?;
        ext(op)?;
        check(
            text(&op["entity_id"], 1, 4096, true) && previous.is_none_or(|l| n(op, "lamport") > l),
        )?;
        previous = Some(n(op, "lamport"));
        validate_operation(collection, op)?;
    }
    Ok(())
}
pub fn validate_operation(collection: &str, op: &Value) -> Result {
    let body = &op["body"];
    let context = &op["context"];
    let entity = s(op, "entity_id");
    let action = s(op, "operation");
    if !matches!(
        collection,
        "palleria.favorite_tag/3"
            | "palleria.search_history/3"
            | "palleria.mute_settings/3"
            | "palleria.view_history/3"
            | "pallasync.device/3"
            | "pallasync.audit/3"
            | "palleria.chain/3"
    ) {
        return Err(ClientError("C_UNSUPPORTED_SCHEMA"));
    }
    if action == "clear" {
        check(
            matches!(
                collection,
                "palleria.favorite_tag/3"
                    | "palleria.search_history/3"
                    | "palleria.mute_settings/3"
                    | "palleria.view_history/3"
            ) && entity == "*"
                && empty(body),
        )?;
        fields(
            context,
            &[(
                "scope",
                if collection == "palleria.view_history/3" {
                    "all|seen|viewed"
                } else {
                    "all"
                },
            )],
            &[],
        )?;
        return Ok(());
    }
    if entity == "*" {
        check(
            action == "upsert"
                && matches!(
                    collection,
                    "palleria.search_history/3" | "palleria.view_history/3"
                ),
        )?;
        fields(context, &[("scope", "retention")], &[])?;
        fields(
            body,
            &[
                (
                    "scope",
                    if collection == "palleria.search_history/3" {
                        "all"
                    } else {
                        "seen|viewed"
                    },
                ),
                ("limit", "int?"),
                ("stop_on_safety_limit", "bool"),
            ],
            &[],
        )?;
        let ceiling = if collection == "palleria.view_history/3" && s(body, "scope") == "seen" {
            10_000_000
        } else {
            1_000_000
        };
        check(body["limit"].is_null() || (1..=ceiling).contains(&n(body, "limit")))?;
        return Ok(());
    }
    if action == "delete" {
        check(empty(body) && empty(context))?;
    }
    match collection {
        "palleria.favorite_tag/3" => {
            check(text(&op["entity_id"], 1, 1024, true))?;
            if action == "upsert" {
                fields(body, &[("tag", "string")], &[])?;
                check(body["tag"] == op["entity_id"])?;
                fields(context, &[("order_key", "string")], &[])?;
                check(valid_order_key(s(context, "order_key")))?;
            }
        }
        "palleria.search_history/3" => {
            if action == "upsert" {
                fields(body, &[("query", "string")], &[])?;
                check(body["query"] == op["entity_id"] && empty(context))?;
            }
        }
        "palleria.mute_settings/3" => {
            let (kind, value) = entity.split_once(':').ok_or_else(invalid)?;
            check(match kind {
                "tag" => (1..=1024).contains(&value.len()) && id(value),
                "user" | "illust" => decimal(value),
                _ => false,
            })?;
            if action == "upsert" {
                fields(
                    body,
                    &[("kind", "tag|user|illust"), ("value", "string")],
                    &[],
                )?;
                check(s(body, "kind") == kind && s(body, "value") == value && empty(context))?;
            }
        }
        "palleria.view_history/3" => {
            let (scope, value) = entity.split_once(':').ok_or_else(invalid)?;
            check(matches!(scope, "seen" | "viewed") && decimal(value))?;
            if action == "upsert" {
                let optional: &[(&str, &str)] = if scope == "viewed" {
                    &[
                        ("title", "text"),
                        ("artistName", "text"),
                        ("imageUrl", "text"),
                        ("pageCount", "int"),
                        ("type", "illust|manga|ugoira"),
                        ("isBookmarked", "bool"),
                        ("xRestrict", "int"),
                        ("illustAiType", "int"),
                        ("tags", "array"),
                    ]
                } else {
                    &[]
                };
                fields(body, &[("id", "int")], optional)?;
                check(n(body, "id") >= 1 && n(body, "id").to_string() == value && empty(context))?;
                for (key, max) in [("title", 4096), ("artistName", 1024), ("imageUrl", 4096)] {
                    if let Some(v) = body.get(key) {
                        check(text(v, 0, max, false))?;
                    }
                }
                if body.get("imageUrl").is_some() {
                    check(
                        url::Url::parse(s(body, "imageUrl"))
                            .is_ok_and(|u| u.scheme() == "https" && u.host_str().is_some()),
                    )?;
                }
                if body.get("pageCount").is_some() {
                    check((1..=1_000_000).contains(&n(body, "pageCount")))?;
                }
                for key in ["xRestrict", "illustAiType"] {
                    if body.get(key).is_some() {
                        check(n(body, key) <= 2)?;
                    }
                }
                if let Some(tags) = body.get("tags") {
                    let tags = tags.as_array().ok_or_else(invalid)?;
                    check(
                        tags.len() <= 1024
                            && tags.iter().all(|v| text(v, 1, 1024, true))
                            && tags
                                .iter()
                                .filter_map(Value::as_str)
                                .collect::<BTreeSet<_>>()
                                .len()
                                == tags.len(),
                    )?;
                }
            }
        }
        "pallasync.device/3" => check(action == "delete" && uuid(entity))?,
        "palleria.chain/3" => check(action == "delete" && decode(entity, Some(32)).is_ok())?,
        "pallasync.audit/3" => {
            check(action == "upsert" && uuid(entity) && empty(context))?;
            fields(
                body,
                &[
                    ("event", "string"),
                    ("subject_id", "string?"),
                    ("object_hash", "b32?"),
                    ("result", "started|succeeded|failed|blocked"),
                ],
                &[("ext", "object")],
            )?;
            let event = s(body, "event");
            check(body["subject_id"].is_null() || text(&body["subject_id"], 0, 128, true))?;
            if event.starts_with("recovery_") {
                check(
                    matches!(
                        event,
                        "recovery_share_set_created"
                            | "recovery_share_set_rotated"
                            | "recovery_share_holder_added"
                            | "recovery_share_holder_removed"
                            | "recovery_ceremony_started"
                            | "recovery_ceremony_succeeded"
                            | "recovery_ceremony_failed"
                            | "recovery_share_lost"
                            | "recovery_share_compromised"
                    ) && body["subject_id"].is_null()
                        && body["object_hash"].is_null(),
                )?;
                fields(&body["ext"], &[("org.palleria.recovery", "object")], &[])?;
                let m = &body["ext"]["org.palleria.recovery"];
                if event.starts_with("recovery_share_set_")
                    || event.starts_with("recovery_ceremony_")
                {
                    check(m["holder_kind"].is_null())?;
                }
                fields(
                    m,
                    &[
                        ("recovery_set_id", "uuid"),
                        ("recovery_secret_fingerprint", "b32"),
                        ("threshold", "int"),
                        ("total_shares", "int"),
                        ("holder_kind", "device|offline?"),
                        ("holder_counts", "object"),
                    ],
                    &[],
                )?;
                fields(
                    &m["holder_counts"],
                    &[("device", "int"), ("offline", "int")],
                    &[],
                )?;
                check(
                    n(m, "threshold") >= 2
                        && n(m, "threshold") < n(m, "total_shares")
                        && n(m, "total_shares") <= 255
                        && n(&m["holder_counts"], "device") + n(&m["holder_counts"], "offline")
                            == n(m, "total_shares"),
                )?;
            } else {
                check(matches!(
                    event,
                    "enrollment"
                        | "revocation"
                        | "rotation"
                        | "self_heal"
                        | "migration"
                        | "compaction"
                        | "relay_migration"
                        | "deletion"
                        | "quarantine"
                ))?;
            }
            ext(body)?;
        }
        _ => unreachable!(),
    }
    Ok(())
}

pub fn parse_inner(bytes: &[u8]) -> Result<crate::models::InnerRecord> {
    let v = jcs::parse(bytes).map_err(|_| invalid())?;
    validate_inner(&v)?;
    serde_json::from_value(v).map_err(|_| invalid())
}
