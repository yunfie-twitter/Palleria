//! §10.7 HTTP wrappers. These hints never establish accepted membership or key possession.
use crate::{
    authority::Authority,
    client_error::{ClientError, Result},
    public_wire::{self as wire, a, fields, n, s},
    strict::verify::*,
};
use serde_json::{Value, json};

fn require(ok: bool) -> Result {
    if ok {
        Ok(())
    } else {
        Err(ClientError("C_SCHEMA_VIOLATION"))
    }
}
pub fn offer_created(value: &Value, offer: &Value) -> Result<Value> {
    fields(
        value,
        &[
            ("request_id", "uuid"),
            ("expires_at_ms", "int"),
            ("offer_hash", "b32"),
        ],
        &[],
    )?;
    let stored = json!({"chain_id":offer["chain_id"],"generation":offer["generation"],"request_id":offer["request_id"],
        "offer_nonce":offer["offer_nonce"],"offering_device_id":offer["approver_device_id"],"expires_at_ms":offer["expires_at_ms"]});
    require(
        value["request_id"] == offer["request_id"]
            && value["expires_at_ms"] == offer["expires_at_ms"]
            && s(value, "offer_hash") == object_hash(&stored),
    )?;
    Ok(value.clone())
}
pub fn request_created(value: &Value, request: &Value) -> Result<Value> {
    wire::validate(request, "request")?;
    fields(
        value,
        &[
            ("request_id", "uuid"),
            ("status", "awaiting_approval"),
            ("request_hash", "b32"),
        ],
        &[],
    )?;
    require(
        value["request_id"] == request["request_id"]
            && s(value, "request_hash") == object_hash(request),
    )?;
    Ok(value.clone())
}
pub fn rejected(value: &Value, offer: &Value) -> Result<Value> {
    fields(
        value,
        &[("request_id", "uuid"), ("status", "rejected")],
        &[],
    )?;
    require(value["request_id"] == offer["request_id"])?;
    Ok(value.clone())
}
pub fn requests_page(
    value: &Value,
    authority: &Authority,
    offer: &Value,
    limit: usize,
) -> Result<Value> {
    require((1..=1000).contains(&limit))?;
    fields(
        value,
        &[
            ("items", "array"),
            ("next_cursor", "text?"),
            ("has_more", "bool"),
            ("log_id", "uuid"),
            ("generation", "u32"),
            ("head_seq", "int"),
        ],
        &[],
    )?;
    if value["log_id"] != authority.pin.log_id
        || value["generation"] != offer["generation"]
        || n(value, "head_seq") < authority.sequence
    {
        return Err(ClientError("C_MISSING_DEPENDENCY"));
    }
    let more = value["has_more"] == true;
    let items = a(value, "items");
    require(
        items.len() <= limit
            && value["next_cursor"].is_string() == more
            && (!more || !items.is_empty()),
    )?;
    if more {
        let cursor = s(value, "next_cursor");
        require(
            !cursor.is_empty()
                && cursor.len() <= 2048
                && cursor.bytes().all(|byte| byte.is_ascii_graphic()),
        )?;
    }
    let mut previous = "";
    for item in items {
        fields(
            item,
            &[
                ("request", "object"),
                ("request_hash", "b32"),
                ("status", "awaiting_approval|approved|rejected"),
                ("offering_device_id", "uuid"),
            ],
            &[],
        )?;
        let request = &item["request"];
        wire::validate(request, "request")?;
        require(
            request["chain_id"] == offer["chain_id"]
                && request["generation"] == offer["generation"]
                && item["offering_device_id"] == offer["approver_device_id"]
                && s(item, "request_hash") == object_hash(request)
                && s(request, "request_id") > previous,
        )?;
        wire::verify(request, s(request, "device_public_key"), CTX_REQUEST)?;
        previous = s(request, "request_id");
    }
    Ok(value.clone())
}
