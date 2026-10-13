//! Exact public action payloads and CAS preconditions (§7.10/§13.4).
//! These are client evidence, never new relay routes or signed wire members.
use crate::{
    client_error::{ClientError, Result},
    public_wire::{self as wire, a, n, s},
    strict::verify::{CTX_ADMIN_OP, object_hash, verify_signed_json},
};
use serde_json::Value;
use std::collections::HashSet;

fn require(value: bool) -> Result {
    if value {
        Ok(())
    } else {
        Err(ClientError("C_SCHEMA_VIOLATION"))
    }
}

type Fields = &'static [(&'static str, &'static str)];

pub fn schema(action: &str, payload: &Value) -> Result {
    let (required, optional): (Fields, Fields) = match action {
        "approve_enrollment" => (&[("device_record", "object"), ("envelopes", "array")], &[]),
        "revoke_device" => (
            &[("epoch", "object"), ("envelopes", "array")],
            &[("device_records", "array")],
        ),
        "reseed" => (
            &[
                ("parameters", "object"),
                ("epoch", "object"),
                ("envelopes", "array"),
                ("compaction", "object"),
                ("device_records", "array"),
            ],
            &[],
        ),
        "delete_chain" => (
            &[
                ("deletion_id", "uuid"),
                ("expected_parameters_hash", "b32"),
                ("expected_epoch_hash", "b32"),
            ],
            &[],
        ),
        "prune_log" => (&[("compaction_id", "uuid"), ("through_seq", "int")], &[]),
        _ => return Err(ClientError("C_SCHEMA_VIOLATION")),
    };
    wire::fields(payload, required, optional).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    for (field, kind) in [
        ("device_record", "device"),
        ("epoch", "epoch"),
        ("parameters", "parameters"),
        ("compaction", "compaction"),
    ] {
        if let Some(value) = payload.get(field) {
            wire::validate(value, kind).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        }
    }
    for (field, kind) in [("envelopes", "envelope"), ("device_records", "device")] {
        if payload.get(field).is_some() {
            let values = a(payload, field);
            let mut identities = HashSet::new();
            for value in values {
                wire::validate(value, kind).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
                require(identities.insert(s(
                    value,
                    if kind == "envelope" {
                        "envelope_id"
                    } else {
                        "device_id"
                    },
                )))?;
            }
        }
    }
    Ok(())
}

/// Object signatures/history and complete membership are checked by Authority; this
/// check binds their exact submitted order and nullable/optional fields to the admin.
pub fn verify(operation: &Value, payload: &Value, parameters: &Value, epoch: &Value) -> Result {
    wire::validate(operation, "admin").map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    schema(s(operation, "action"), payload)?;
    require(
        operation["chain_id"] == parameters["chain_id"]
            && operation["generation"] == parameters["generation"],
    )?;
    if !verify_signed_json(
        s(parameters, "admin_public_key"),
        s(operation, "signature"),
        operation,
        CTX_ADMIN_OP,
    )
    .unwrap_or(false)
    {
        return Err(ClientError("C_SIGNATURE_INVALID"));
    }
    require(
        s(operation, "expected_parameters_hash") == object_hash(parameters)
            && s(operation, "expected_epoch_hash") == object_hash(epoch),
    )?;
    require(s(operation, "target_hash") == object_hash(payload))?;
    for field in ["device_record", "parameters", "epoch", "compaction"] {
        if let Some(value) = payload.get(field) {
            require(value["chain_id"] == operation["chain_id"])?;
            let expected =
                n(operation, "generation") + u64::from(s(operation, "action") == "reseed");
            require(n(value, "generation") == expected)?;
        }
    }
    for field in ["envelopes", "device_records"] {
        for value in a(payload, field) {
            require(
                value["chain_id"] == operation["chain_id"]
                    && n(value, "generation")
                        == n(operation, "generation")
                            + u64::from(s(operation, "action") == "reseed"),
            )?;
        }
    }
    match s(operation, "action") {
        "approve_enrollment" => {
            require(!a(payload, "envelopes").is_empty())?;
            let device = &payload["device_record"];
            require(a(payload, "envelopes").iter().all(|e| {
                e["recipient_kind"] == "device" && e["recipient_device_id"] == device["device_id"]
            }))?;
            require(
                a(payload, "envelopes")
                    .iter()
                    .any(|e| e["epoch"] == epoch["epoch"]),
            )?;
        }
        "revoke_device" => {
            require(!a(&payload["epoch"], "revoked_device_ids").is_empty())?;
            require(
                n(&payload["epoch"], "epoch") == n(epoch, "epoch") + 1
                    && s(&payload["epoch"], "previous_epoch_hash") == object_hash(epoch),
            )?;
            require(
                payload.get("device_records").is_none()
                    || payload["epoch"]["reason"] == "emergency",
            )?;
        }
        "reseed" => {
            require(
                payload["epoch"]["reason"] == "reseed" && !a(payload, "device_records").is_empty(),
            )?;
            require(
                n(&payload["epoch"], "epoch") == n(epoch, "epoch") + 1
                    && s(&payload["epoch"], "previous_epoch_hash") == object_hash(epoch),
            )?;
            require(
                s(&payload["parameters"], "previous_parameters_hash") == object_hash(parameters),
            )?;
        }
        "delete_chain" => require(
            payload["expected_parameters_hash"] == operation["expected_parameters_hash"]
                && payload["expected_epoch_hash"] == operation["expected_epoch_hash"],
        )?,
        "prune_log" => {}
        _ => unreachable!("closed action schema"),
    }
    Ok(())
}

/// Sorting relay control entries cannot change an admin's signed array order. A
/// supplied payload may reorder the same complete objects, never substitute/add one.
pub fn same_objects(left: &[Value], right: &[Value]) -> bool {
    left.len() == right.len() && {
        let mut left = left.iter().map(object_hash).collect::<Vec<_>>();
        let mut right = right.iter().map(object_hash).collect::<Vec<_>>();
        left.sort();
        right.sort();
        left == right
    }
}
