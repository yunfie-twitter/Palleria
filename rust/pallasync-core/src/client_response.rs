//! Unsigned HTTP wrappers are validated hints, never signed authority (§10/§19).
use crate::{
    attestations::{self, RelayPin},
    client_error::{ClientError, Result},
    public_wire::{self as wire, a, fields, n, s},
};
use serde_json::{Value, json};

fn require(ok: bool) -> Result {
    if ok {
        Ok(())
    } else {
        Err(ClientError("C_SCHEMA_VIOLATION"))
    }
}
pub fn log_head(value: &Value) -> Result<Value> {
    fields(
        value,
        &[
            ("log_id", "uuid"),
            ("generation", "u32"),
            ("head_seq", "int"),
            ("head_hash", "b32"),
            ("min_available_seq", "int"),
            ("epoch_hash", "b32"),
            ("parameters_hash", "b32"),
        ],
        &[],
    )
    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    require(
        n(value, "min_available_seq") > 0
            && n(value, "min_available_seq") <= n(value, "head_seq") + 1,
    )?;
    Ok(value.clone())
}

/// §10.3/§11.6: validate the selected initial history's parameters against a locally
/// derived or independently authenticated public identity. A head advertisement is not a prefix proof.
pub fn initial_parameters(
    value: &Value,
    identity: &Value,
    origin: &str,
    key: &str,
) -> Result<crate::authority::Authority> {
    fields(
        identity,
        &[("chain_id", "b32"), ("admin_public_key", "b32")],
        &[],
    )?;
    fields(
        value,
        &[
            ("parameters", "object"),
            ("parameters_hash", "b32"),
            ("epoch_hash", "b32"),
            ("log_id", "uuid"),
            ("generation", "u32"),
            ("head_seq", "int"),
            ("head_hash", "b32"),
            ("server_time_ms", "int"),
            ("quota", "object"),
        ],
        &[],
    )?;
    fields(
        &value["quota"],
        &[("used_bytes", "int"), ("max_bytes", "int?")],
        &[],
    )?;
    let parameters = &value["parameters"];
    wire::validate(parameters, "parameters")?;
    if parameters["chain_id"] != identity["chain_id"]
        || parameters["admin_public_key"] != identity["admin_public_key"]
    {
        return Err(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"));
    }
    wire::verify(
        parameters,
        s(identity, "admin_public_key"),
        crate::strict::verify::CTX_PARAMETERS,
    )?;
    require(
        crate::strict::verify::object_hash(parameters) == s(value, "parameters_hash")
            && parameters["generation"] == value["generation"],
    )?;
    crate::authority::Authority::new(
        RelayPin {
            origin: origin.into(),
            chain_id: s(identity, "chain_id").into(),
            public_key: key.into(),
            log_id: s(value, "log_id").into(),
        },
        s(identity, "admin_public_key").into(),
    )
}
/// Read views may advance concurrently. A differing unsigned wrapper requires reconciliation,
/// and is not independent signed fork evidence. The caller may repeat its read once (§11.5).
pub fn compare_log_heads(log: &Value, signed: &Value, generation: u32) -> Result {
    log_head(log)?;
    if n(log, "generation") != u64::from(generation)
        || [
            "log_id",
            "head_seq",
            "head_hash",
            "min_available_seq",
            "epoch_hash",
            "parameters_hash",
        ]
        .iter()
        .any(|key| log[*key] != signed[*key])
    {
        return Err(ClientError("C_MISSING_DEPENDENCY"));
    }
    Ok(())
}
pub fn page(
    value: &Value,
    kind: &str,
    pin: &RelayPin,
    generation: u32,
    after: u64,
    limit: usize,
) -> Result {
    pin.validate()?;
    require((1..=1000).contains(&limit))?;
    fields(
        value,
        &[
            ("items", "array"),
            ("next_cursor", "text?"),
            ("has_more", "bool"),
            ("scan_through_seq", "int"),
            ("log_id", "uuid"),
            ("generation", "u32"),
            ("head_seq", "int"),
            ("head_hash", "b32"),
        ],
        &[],
    )
    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    if s(value, "log_id") != pin.log_id {
        return Err(ClientError("C_LOG_HASH_MISMATCH"));
    }
    if n(value, "generation") < u64::from(generation) {
        return Err(ClientError("C_ROLLBACK_DETECTED"));
    }
    if n(value, "generation") > u64::from(generation) {
        return Err(ClientError("C_MISSING_DEPENDENCY"));
    }
    let cursor = value["next_cursor"].as_str();
    require(value["has_more"].as_bool() == Some(cursor.is_some()))?;
    require(cursor.is_none_or(|c| {
        !c.is_empty()
            && c.len() <= 2048
            && c.bytes()
                .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
    }))?;
    require(
        a(value, "items").len() <= limit
            && after <= n(value, "scan_through_seq")
            && n(value, "scan_through_seq") <= n(value, "head_seq"),
    )?;
    let mut previous = after;
    for entry in a(value, "items") {
        attestations::validate_log_entry(entry).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        require(
            n(entry, "relay_seq") > previous
                && n(entry, "relay_seq") <= n(value, "scan_through_seq"),
        )?;
        require(kind != "log" || n(entry, "relay_seq") == previous + 1)?;
        require(kind == "log" || (kind == "records" && s(entry, "kind") == "record"))?;
        require(
            s(&entry["object"], "chain_id") == pin.chain_id
                && (n(&entry["object"], "generation") == u64::from(generation)
                    || (kind == "log"
                        && n(&entry["object"], "generation") < u64::from(generation))),
        )?;
        previous = n(entry, "relay_seq");
    }
    if kind == "log" {
        require(previous == n(value, "scan_through_seq"))?;
        require(value["has_more"] == true || previous == n(value, "head_seq"))?;
    }
    require(!value["has_more"].as_bool().unwrap() || n(value, "scan_through_seq") > after)?;
    if n(value, "head_seq") == 0 {
        require(s(value, "head_hash") == attestations::data_zero(&pin.chain_id, &pin.log_id)?)?;
    }
    Ok(())
}

/// RFC 9457 allows unknown extension members only here. Retain only the validated
/// clock/wait hints required by §6.3/§17.8; none grants signed protocol authority.
pub fn problem(value: &Value, status: u16) -> Result<Value> {
    require(value.is_object() && value["type"] == "about:blank" && value["title"].is_string())?;
    require(
        value["status"].as_u64() == Some(u64::from(status))
            && wire::check_type(&value["server_time_ms"], "int"),
    )?;
    let code = s(value, "code");
    let Some((catalogue_status, retryable)) = crate::error_catalogue::server(code) else {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    };
    require(catalogue_status == status && value["retryable"].as_bool() == Some(retryable))?;
    if let Some(detail) = value.get("detail") {
        require(
            detail
                .as_str()
                .is_some_and(|s| s.len() <= 1024 && !s.chars().any(|c| c.is_control())),
        )?;
    }
    if let Some(instance) = value.get("instance") {
        require(instance.as_str().is_some_and(|s| {
            s.starts_with('/') && !s.contains(['?', '#']) && !s.chars().any(|c| c.is_control())
        }))?;
    }
    if let Some(context) = value.get("context") {
        fields(
            context,
            &[],
            &[
                ("epoch", "u32"),
                ("epoch_hash", "b32"),
                ("generation", "u32"),
                ("parameters_hash", "b32"),
                ("log_id", "uuid"),
                ("head_seq", "int"),
                ("head_hash", "b32"),
                ("min_available_seq", "int"),
                ("compaction_id", "uuid"),
                ("retry_after_ms", "int"),
                ("scope", "text"),
                ("used_bytes", "int"),
                ("max_bytes", "int?"),
                ("shutdown_at_ms", "int?"),
                ("migration_origin", "text?"),
                ("deletion_id", "uuid"),
                ("assurance", "crypto-shred|backup-expiry"),
                ("delete_after_ms", "int?"),
                ("receipt_hash", "b32"),
            ],
        )
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    }
    Ok(
        json!({"status":status,"code":code,"retryable":retryable,"server_time_ms":value["server_time_ms"],"retry_after_ms":value["context"]["retry_after_ms"]}),
    )
}

/// Verify the statement before comparing it. Retain valid signed contradictory views as evidence.
/// A changed log incarnation is observed here, never installed as a replacement pin.
pub fn observed_transparency_head(value: &Value, pin: &RelayPin) -> Result<Value> {
    fields(value, &[("head", "object")], &[]).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let head = &value["head"];
    let observed_pin = RelayPin {
        log_id: s(head, "log_id").to_owned(),
        ..pin.clone()
    };
    attestations::validate_head(head, &observed_pin)?;
    Ok(head.clone())
}

pub fn transparency_head(
    value: &Value,
    pin: &RelayPin,
    anchor: &crate::attestations::TransparencyAnchor,
    verified_seq: u64,
    verified_hash: &str,
    parameters_hash: &str,
    epoch_hash: &str,
) -> Result<Value> {
    let head = observed_transparency_head(value, pin)?;
    if s(&head, "log_id") != pin.log_id {
        return Err(ClientError("C_LOG_HASH_MISMATCH"));
    }
    if s(&head, "transparency_id") != anchor.transparency_id
        || n(&head, "entry_index") < anchor.index
        || n(&head, "head_seq") < verified_seq
    {
        return Err(ClientError("C_ROLLBACK_DETECTED"));
    }
    if n(&head, "head_seq") == verified_seq && s(&head, "head_hash") != verified_hash {
        return Err(ClientError("C_LOG_HASH_MISMATCH"));
    }
    if n(&head, "entry_index") == anchor.index && s(&head, "transparency_hash") != anchor.hash {
        return Err(ClientError("C_TRANSPARENCY_INVALID"));
    }
    // Changed signed authority heads require history/commitment processing before writing.
    if s(&head, "parameters_hash") != parameters_hash || s(&head, "epoch_hash") != epoch_hash {
        return Err(ClientError("C_MISSING_DEPENDENCY"));
    }
    Ok(head)
}
pub fn transparency_page(
    value: &Value,
    pin: &RelayPin,
    anchor: &crate::attestations::TransparencyAnchor,
    target: &Value,
    limit: usize,
) -> Result<crate::attestations::TransparencyAnchor> {
    fields(
        value,
        &[
            ("items", "array"),
            ("head", "object"),
            ("anchor_index", "int"),
            ("anchor_hash", "b32"),
            ("next_cursor", "text?"),
            ("has_more", "bool"),
        ],
        &[],
    )
    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    require(
        value["head"] == *target
            && n(value, "anchor_index") == anchor.index
            && s(value, "anchor_hash") == anchor.hash
            && a(value, "items").len() <= limit,
    )?;
    let next =
        crate::attestations::verify_transparency_page(anchor, a(value, "items"), target, pin)?;
    let more = next.index < n(target, "entry_index");
    require(value["has_more"].as_bool() == Some(more) && value["next_cursor"].is_string() == more)?;
    if more {
        require(
            s(value, "next_cursor").len() <= 2048
                && !s(value, "next_cursor").is_empty()
                && s(value, "next_cursor")
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-'),
        )?;
    } else {
        require(value["next_cursor"].is_null())?;
    }
    Ok(next)
}
