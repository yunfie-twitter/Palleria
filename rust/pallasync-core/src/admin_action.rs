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
            verify_reseed_objects(operation, payload)?;
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

/// Re-seed is an atomic replacement generation: every fresh member has one signed
/// certificate and device record, every member has exactly one new-epoch envelope,
/// optional root-recovery envelope hash is exact, and compaction is signed by a fresh
/// member. This validates only public evidence; it never opens any envelope.
fn verify_reseed_objects(operation: &Value, payload: &Value) -> Result {
    let epoch = &payload["epoch"];
    let generation = n(operation, "generation") + 1;
    let chain_id = s(operation, "chain_id");
    let mut members = HashSet::new();
    for member in a(epoch, "members") {
        let id = member.as_str().ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        require(members.insert(id))?;
    }
    require(!members.is_empty())?;

    let admin_key = s(&payload["parameters"], "admin_public_key");
    let mut devices = std::collections::HashMap::new();
    for device in a(payload, "device_records") {
        require(
            s(device, "chain_id") == chain_id
                && n(device, "generation") == generation
                && members.contains(s(device, "device_id")),
        )?;
        let id = s(device, "device_id");
        require(devices.insert(id, device).is_none())?;
        let certificate = &device["enrollment"];
        require(
            s(certificate, "signer_kind") == "admin" && certificate["signer_device_id"].is_null(),
        )?;
        verify_signed(
            device,
            s(device, "device_public_key"),
            crate::strict::verify::CTX_DEVICE_RECORD,
        )?;
        verify_signed(
            certificate,
            admin_key,
            crate::strict::verify::CTX_ENROLLMENT,
        )?;
    }
    require(devices.len() == members.len())?;

    let mut envelope_members = HashSet::new();
    let mut recovery_envelopes = Vec::new();
    for envelope in a(payload, "envelopes") {
        require(
            s(envelope, "chain_id") == chain_id
                && n(envelope, "generation") == generation
                && envelope["epoch"] == epoch["epoch"]
                && envelope["previous_epoch_hash"] == epoch["previous_epoch_hash"]
                && envelope["epoch_commitment"] == epoch["epoch_commitment"]
                && s(envelope, "signer_kind") == "admin"
                && envelope["signer_device_id"].is_null(),
        )?;
        verify_signed(envelope, admin_key, crate::strict::verify::CTX_ENVELOPE)?;
        if s(envelope, "recipient_kind") == "device" {
            let id = s(envelope, "recipient_device_id");
            require(members.contains(id) && envelope_members.insert(id))?;
            let device = devices.get(id).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
            let recipient_key =
                crate::strict::verify::decode(s(device, "device_kex_public_key"), Some(32))
                    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
            require(
                s(envelope, "recipient_key_hash")
                    == crate::strict::verify::hash_bytes(&recipient_key),
            )?;
        } else {
            recovery_envelopes.push(envelope);
        }
    }
    require(envelope_members == members)?;
    match epoch["recovery_envelope_hash"].as_str() {
        Some(expected) => {
            require(
                recovery_envelopes.len() == 1 && object_hash(recovery_envelopes[0]) == expected,
            )?;
        }
        None => require(recovery_envelopes.is_empty())?,
    }

    let compaction = &payload["compaction"];
    require(
        s(compaction, "chain_id") == chain_id
            && n(compaction, "generation") == generation
            && s(compaction, "epoch_hash") == object_hash(epoch),
    )?;
    let author = s(compaction, "device_id");
    let device = devices
        .get(author)
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
    verify_signed(
        compaction,
        s(device, "device_public_key"),
        crate::strict::verify::CTX_COMPACTION,
    )?;
    Ok(())
}

fn verify_signed(value: &Value, key: &str, context: &[u8]) -> Result {
    require(
        crate::strict::verify::verify_signed_json(key, s(value, "signature"), value, context)
            .unwrap_or(false),
    )
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
