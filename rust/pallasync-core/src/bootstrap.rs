//! Selected-source bootstrap boundaries (§10.4/§10.17/§11.6/§18.4).
use crate::{
    attestations::{self, RelayPin},
    authority::Authority,
    client_error::{ClientError, Result},
    public_wire::{self as wire, fields, n, s},
    strict::verify::object_hash,
};
use serde_json::{Value, json};

fn require(ok: bool, code: &'static str) -> Result {
    if ok { Ok(()) } else { Err(ClientError(code)) }
}

/// Recheck retained immutable bytes at a contiguous materialization boundary (§11.2).
pub fn retained_log_link(
    entry: &Value,
    receipt: &Value,
    pin: &RelayPin,
    after: u64,
    previous: &str,
) -> Result<String> {
    attestations::validate_log_entry(entry)?;
    require(
        n(entry, "relay_seq")
            == after
                .checked_add(1)
                .ok_or(ClientError("C_COUNTER_EXHAUSTED"))?,
        "C_MISSING_DEPENDENCY",
    )?;
    attestations::verify_receipt(receipt, &entry["object"], Some(entry), pin)?;
    require(
        s(receipt, "previous_head_hash") == previous,
        "C_LOG_HASH_MISMATCH",
    )?;
    let next = attestations::data_link(previous, entry)?;
    require(
        s(receipt, "resulting_head_hash") == next,
        "C_LOG_HASH_MISMATCH",
    )?;
    Ok(next)
}

pub fn receipt_response(value: &Value, expected: &Value, pin: &RelayPin) -> Result<Value> {
    fields(value, &[("entry", "object?"), ("receipt", "object")], &[])?;
    require(value["entry"] == *expected, "C_ACCEPTANCE_UNPROVEN")?;
    attestations::verify_receipt(&value["receipt"], &expected["object"], Some(expected), pin)?;
    Ok(value["receipt"].clone())
}

/// A signature authenticates the advertised cut. Only a verified complete H prefix may install it.
pub fn history_cut(authority: &Authority, response: &Value) -> Result<Value> {
    authority.complete_cut()?;
    fields(response, &[("head", "object")], &[])?;
    let head = &response["head"];
    attestations::validate_head(head, &authority.pin)?;
    if n(head, "head_seq") < authority.sequence {
        return Err(ClientError("C_ROLLBACK_DETECTED"));
    }
    require(
        n(head, "head_seq") == authority.sequence,
        "C_MISSING_DEPENDENCY",
    )?;
    require(
        s(head, "head_hash") == authority.head_hash,
        "C_LOG_HASH_MISMATCH",
    )?;
    let parameters = authority
        .parameters
        .as_ref()
        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
    let epoch = authority.current_epoch()?;
    require(
        s(head, "parameters_hash") == object_hash(parameters)
            && s(head, "epoch_hash") == object_hash(&epoch.record),
        "C_LOG_HASH_MISMATCH",
    )?;
    let zero = attestations::transparency_zero(
        &authority.pin.chain_id,
        s(head, "transparency_id"),
        &authority.pin.key_id()?,
    )?;
    Ok(
        json!({"head":head,"zero_anchor":{"transparency_id":head["transparency_id"],"index":0,"hash":zero},
        "data_zero":attestations::data_zero(&authority.pin.chain_id,&authority.pin.log_id)?,"generation":authority.generation()?}),
    )
}

pub fn recovery_response(value: &Value, authority: &Authority, epoch: u32) -> Result<Value> {
    fields(value, &[("envelope", "object")], &[])?;
    let env = &value["envelope"];
    wire::validate(env, "envelope")?;
    let known = authority
        .epochs
        .get(&epoch)
        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
    require(
        env["recipient_kind"] == "recovery"
            && s(env, "chain_id") == authority.pin.chain_id
            && n(env, "epoch") == u64::from(epoch)
            && env["generation"] == known.record["generation"]
            && env["previous_epoch_hash"] == known.record["previous_epoch_hash"]
            && env["epoch_commitment"] == known.record["epoch_commitment"]
            && object_hash(env) == s(&known.record, "recovery_envelope_hash"),
        "C_EPOCH_FORK",
    )?;
    // The full object hash was signed by the historically authorized epoch signer and
    // its own signature was verified before accepting that epoch's complete envelope bundle.
    Ok(env.clone())
}

pub fn enrollment_acceptance(value: &Value, submitted: &Value, pin: &RelayPin) -> Result<Value> {
    fields(
        value,
        &[
            ("device_id", "uuid"),
            ("status", "pending_keys"),
            ("device_record_hash", "b32"),
            ("upload_receipts", "array"),
        ],
        &[],
    )?;
    fields(
        submitted,
        &[
            ("device_record", "object"),
            ("envelopes", "array"),
            ("admin_operation", "object?"),
        ],
        &[],
    )?;
    require(
        value["device_id"] == submitted["device_record"]["device_id"]
            && s(value, "device_record_hash") == object_hash(&submitted["device_record"]),
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    wire::validate(&submitted["device_record"], "device")?;
    for envelope in submitted["envelopes"]
        .as_array()
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))?
    {
        wire::validate(envelope, "envelope")?;
    }
    require(
        (submitted["device_record"]["enrollment"]["signer_kind"] == "admin")
            == !submitted["admin_operation"].is_null(),
        "C_SCHEMA_VIOLATION",
    )?;
    let mut objects = vec![&submitted["device_record"]];
    if !submitted["admin_operation"].is_null() {
        objects.push(&submitted["admin_operation"]);
    }
    objects.extend(
        submitted["envelopes"]
            .as_array()
            .ok_or(ClientError("C_SCHEMA_VIOLATION"))?
            .iter(),
    );
    let receipts = value["upload_receipts"]
        .as_array()
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
    require(receipts.len() == objects.len(), "C_ACCEPTANCE_UNPROVEN")?;
    let mut ids = std::collections::HashSet::new();
    let mut last = 0;
    for receipt in receipts {
        attestations::validate_receipt(receipt, pin)?;
        let hash = s(receipt, "object_hash");
        require(
            objects.iter().filter(|v| object_hash(v) == hash).count() == 1
                && ids.insert(hash)
                && n(receipt, "relay_seq") > last,
            "C_ACCEPTANCE_UNPROVEN",
        )?;
        last = n(receipt, "relay_seq");
    }
    Ok(value.clone())
}

/// §10.10/§17.9: acceptance is bound to the exact complete proposal. This
/// observation does not replace replay of H, T or the authority install.
pub fn epoch_acceptance(value: &Value, submitted: &Value, pin: &RelayPin) -> Result<Value> {
    fields(
        value,
        &[
            ("epoch_hash", "b32"),
            ("receipt", "object"),
            ("upload_receipts", "array"),
        ],
        &[],
    )?;
    epoch_acceptance_objects(value, submitted, pin)
}

pub fn revocation_acceptance(
    value: &Value,
    submitted: &Value,
    pin: &RelayPin,
    device: &str,
) -> Result<Value> {
    fields(
        value,
        &[
            ("device_id", "uuid"),
            ("status", "revoked"),
            ("epoch_hash", "b32"),
            ("receipt", "object"),
            ("upload_receipts", "array"),
        ],
        &[],
    )?;
    require(
        value["device_id"] == device
            && submitted["epoch"]["revoked_device_ids"]
                .as_array()
                .is_some_and(|ids| ids.iter().any(|id| id == device)),
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    epoch_acceptance_objects(value, submitted, pin)
}

fn epoch_acceptance_objects(value: &Value, submitted: &Value, pin: &RelayPin) -> Result<Value> {
    fields(
        submitted,
        &[
            ("expected_epoch_hash", "b32"),
            ("epoch", "object"),
            ("envelopes", "array"),
            ("admin_operation", "object?"),
        ],
        &[("device_records", "array")],
    )?;
    wire::validate(&submitted["epoch"], "epoch")?;
    require(
        value["epoch_hash"] == object_hash(&submitted["epoch"]),
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    let mut objects = vec![&submitted["epoch"]];
    if !submitted["admin_operation"].is_null() {
        wire::validate(&submitted["admin_operation"], "admin")?;
        objects.push(&submitted["admin_operation"]);
    }
    for env in submitted["envelopes"].as_array().unwrap() {
        wire::validate(env, "envelope")?;
        objects.push(env);
    }
    if let Some(devices) = submitted.get("device_records") {
        require(
            submitted["epoch"]["reason"] == "emergency",
            "C_SCHEMA_VIOLATION",
        )?;
        for device in devices.as_array().unwrap() {
            wire::validate(device, "device")?;
            objects.push(device);
        }
    }
    let receipts = value["upload_receipts"].as_array().unwrap();
    require(receipts.len() == objects.len(), "C_ACCEPTANCE_UNPROVEN")?;
    let mut seen = std::collections::HashSet::new();
    let mut previous: Option<&Value> = None;
    for receipt in receipts {
        attestations::validate_receipt(receipt, pin)?;
        let hash = s(receipt, "object_hash");
        require(
            objects.iter().filter(|v| object_hash(v) == hash).count() == 1 && seen.insert(hash),
            "C_ACCEPTANCE_UNPROVEN",
        )?;
        if let Some(prev) = previous {
            require(
                n(receipt, "relay_seq") == n(prev, "relay_seq") + 1
                    && receipt["previous_head_hash"] == prev["resulting_head_hash"],
                "C_RECEIPT_INVALID",
            )?;
        }
        previous = Some(receipt);
    }
    let last = previous.ok_or(ClientError("C_ACCEPTANCE_UNPROVEN"))?;
    attestations::validate_log_entry(&value["receipt"])?;
    require(
        value["receipt"]["kind"] == "epoch" && value["receipt"]["object"] == submitted["epoch"],
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    attestations::verify_receipt(last, &submitted["epoch"], Some(&value["receipt"]), pin)?;
    Ok(value.clone())
}

pub fn keys_ack(value: &Value, device: &str, epoch_hash: &str) -> Result<Value> {
    fields(
        value,
        &[
            ("device_id", "uuid"),
            ("status", "active"),
            ("epoch_hash", "b32"),
        ],
        &[],
    )?;
    require(
        s(value, "device_id") == device && s(value, "epoch_hash") == epoch_hash,
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    Ok(value.clone())
}

pub fn own_envelope_page(value: &Value, expected: &Value, authority: &Authority) -> Result<Value> {
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
    require(
        value["log_id"] == authority.pin.log_id
            && n(value, "generation") == u64::from(authority.generation()?),
        "C_MISSING_DEPENDENCY",
    )?;
    wire::validate(expected, "envelope")?;
    require(expected["recipient_kind"] == "device", "C_SCHEMA_VIOLATION")?;
    let items = value["items"]
        .as_array()
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
    require(
        items.len() <= 200 && value["has_more"].as_bool() == Some(!value["next_cursor"].is_null()),
        "C_SCHEMA_VIOLATION",
    )?;
    for item in items {
        wire::validate(item, "envelope")?;
        require(
            item["recipient_kind"] == "device"
                && item["recipient_device_id"] == expected["recipient_device_id"]
                && item["epoch"] == expected["epoch"],
            "C_SCHEMA_VIOLATION",
        )?;
    }
    require(
        !value["has_more"].as_bool().unwrap()
            && items.iter().filter(|v| *v == expected).count() == 1,
        "C_MISSING_DEPENDENCY",
    )?;
    Ok(expected.clone())
}
