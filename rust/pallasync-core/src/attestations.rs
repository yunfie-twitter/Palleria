//! §§7.12/17.3/17.9. Relay commitments authenticate storage claims, not client authority.
use crate::{
    client_error::{ClientError, Result},
    public_wire::{self as wire, a, fields, n, s},
    strict::{jcs, verify::*},
};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use serde_json::json;
use sha2::{Digest, Sha256};
use std::collections::{BTreeMap, HashSet};

fn schema() -> ClientError {
    ClientError("C_SCHEMA_VIOLATION")
}
fn require(condition: bool, code: &'static str) -> Result {
    if condition {
        Ok(())
    } else {
        Err(ClientError(code))
    }
}

/// Protected local pin. Never populated from an unconfirmed response or error hint.
#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct RelayPin {
    pub origin: String,
    pub chain_id: String,
    pub public_key: String,
    pub log_id: String,
}
impl RelayPin {
    pub fn validate(&self) -> Result {
        require(
            self.origin.starts_with("https://")
                && crate::crypto::normalize_origin(&self.origin)
                    .ok()
                    .as_deref()
                    == Some(&self.origin),
            "C_RELAY_KEY_MISMATCH",
        )?;
        require(
            valid_ed_key(&self.public_key)
                && decode(&self.chain_id, Some(32)).is_ok()
                && wire::uuid(&self.log_id),
            "C_RELAY_KEY_MISMATCH",
        )
    }
    pub fn key_id(&self) -> Result<String> {
        Ok(hash_bytes(
            &decode(&self.public_key, Some(32)).map_err(|_| schema())?,
        ))
    }
    fn verify(&self, value: &Value, context: &[u8]) -> Result {
        self.validate()?;
        require(
            s(value, "chain_id") == self.chain_id && s(value, "log_id") == self.log_id,
            "C_LOG_HASH_MISMATCH",
        )?;
        require(
            s(value, "relay_key_id") == self.key_id()?,
            "C_RELAY_KEY_MISMATCH",
        )?;
        require(
            verify_signature(
                &self.public_key,
                s(value, "relay_signature"),
                &signing_bytes(value, context, "relay_signature"),
            )
            .unwrap_or(false),
            "C_SIGNATURE_INVALID",
        )
    }
}

fn link(previous: &str, index: u64, hash: &str, context: &[u8]) -> Result<String> {
    let mut digest = Sha256::new();
    digest.update(context);
    digest.update(decode(previous, Some(32)).map_err(|_| schema())?);
    digest.update(index.to_be_bytes());
    digest.update(decode(hash, Some(32)).map_err(|_| schema())?);
    Ok(hash_bytes_raw(&digest.finalize()))
}
fn hash_bytes_raw(value: &[u8]) -> String {
    use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
    URL_SAFE_NO_PAD.encode(value)
}
pub fn data_zero(chain: &str, log: &str) -> Result<String> {
    let mut input = b"PALLASYNC-LOG-v3\0".to_vec();
    input.extend(decode(chain, Some(32)).map_err(|_| schema())?);
    input.extend(uuid::Uuid::parse_str(log).map_err(|_| schema())?.as_bytes());
    require(wire::uuid(log), "C_SCHEMA_VIOLATION")?;
    Ok(hash_bytes(&input))
}
pub fn transparency_zero(chain: &str, id: &str, key_id: &str) -> Result<String> {
    let mut input = b"PALLASYNC-TRANSPARENCY-v3\0".to_vec();
    input.extend(decode(chain, Some(32)).map_err(|_| schema())?);
    input.extend(uuid::Uuid::parse_str(id).map_err(|_| schema())?.as_bytes());
    input.extend(decode(key_id, Some(32)).map_err(|_| schema())?);
    require(wire::uuid(id), "C_SCHEMA_VIOLATION")?;
    Ok(hash_bytes(&input))
}
pub fn validate_log_entry(entry: &Value) -> Result {
    fields(
        entry,
        &[
            ("relay_seq", "int"),
            ("received_at_ms", "int"),
            (
                "kind",
                "parameters|device|epoch|envelope|compaction|admin|record",
            ),
            ("object", "object"),
        ],
        &[("committed_at_ms", "int")],
    )?;
    require(
        n(entry, "relay_seq") > 0
            && (s(entry, "kind") == "epoch") == entry.get("committed_at_ms").is_some(),
        "C_SCHEMA_VIOLATION",
    )?;
    if s(entry, "kind") == "epoch" {
        require(
            entry["committed_at_ms"] == entry["received_at_ms"],
            "C_SCHEMA_VIOLATION",
        )?;
    }
    wire::validate(&entry["object"], s(entry, "kind"))
}
pub fn data_link(previous: &str, entry: &Value) -> Result<String> {
    validate_log_entry(entry)?;
    link(previous, n(entry, "relay_seq"), &object_hash(entry), b"")
}

pub fn validate_receipt(receipt: &Value, pin: &RelayPin) -> Result {
    fields(
        receipt,
        &[
            ("type", "PALLASYNC-UPLOAD-RECEIPT-v3"),
            ("version", "3.0"),
            ("receipt_id", "uuid"),
            ("chain_id", "b32"),
            ("log_id", "uuid"),
            ("relay_key_id", "b32"),
            ("relay_seq", "int?"),
            ("object_hash", "b32"),
            ("entry_hash", "b32?"),
            ("previous_head_hash", "b32"),
            ("resulting_head_hash", "b32"),
            ("received_at_ms", "int"),
            ("transparency_id", "uuid"),
            ("transparency_index", "int"),
            ("transparency_hash", "b32"),
            ("relay_signature", "b64"),
        ],
        &[],
    )?;
    require(
        n(receipt, "transparency_index") > 0
            && receipt["relay_seq"].is_null() == receipt["entry_hash"].is_null()
            && (receipt["relay_seq"].is_null() || n(receipt, "relay_seq") > 0),
        "C_SCHEMA_VIOLATION",
    )?;
    pin.verify(receipt, CTX_RECEIPT)
}

/// Validate the immutable snapshot-part readback wrapper (§7.12.1/§10.11). Its receipt is
/// deliberately outside the data-log sequence and must commit an unchanged data head.
pub fn validate_snapshot_part_response(
    response: &Value,
    expected_record_id: &str,
    pin: &RelayPin,
) -> Result<Value> {
    fields(
        response,
        &[("record", "object"), ("upload_receipt", "object")],
        &[],
    )?;
    let record = &response["record"];
    let receipt = &response["upload_receipt"];
    wire::validate(record, "record")?;
    validate_receipt(receipt, pin)?;
    require(
        s(record, "record_id") == expected_record_id
            && s(record, "chain_id") == pin.chain_id
            && s(receipt, "object_hash") == object_hash(record)
            && receipt["relay_seq"].is_null()
            && receipt["entry_hash"].is_null()
            && s(receipt, "previous_head_hash") == s(receipt, "resulting_head_hash"),
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    Ok(json!({"record":record,"upload_receipt":receipt}))
}

/// Validate POST C/snapshots/parts and its null-sequence receipts (§10.11/§10.17).
/// The response's accepted/rejected IDs must account for the exact submitted batch; receipts
/// bind to each accepted ciphertext without adding any event-log sequence.
pub fn validate_snapshot_part_upload(
    response: &Value,
    submitted: &[Value],
    pin: &RelayPin,
) -> Result<Value> {
    fields(
        response,
        &[
            ("accepted", "array"),
            ("rejected", "array"),
            ("upload_receipts", "array"),
        ],
        &[],
    )?;
    require(
        !submitted.is_empty() && submitted.len() <= 64,
        "C_SCHEMA_VIOLATION",
    )?;

    let mut by_id = BTreeMap::new();
    let mut by_hash = BTreeMap::new();
    for record in submitted {
        wire::validate(record, "record")?;
        require(s(record, "chain_id") == pin.chain_id, "C_LOG_HASH_MISMATCH")?;
        let id = s(record, "record_id").to_owned();
        let hash = object_hash(record);
        require(
            by_id.insert(id.clone(), hash.clone()).is_none(),
            "C_DEVICE_EQUIVOCATION",
        )?;
        require(by_hash.insert(hash, id).is_none(), "C_DEVICE_EQUIVOCATION")?;
    }

    let mut accepted = HashSet::new();
    for id in a(response, "accepted") {
        let id = id.as_str().ok_or_else(schema)?;
        require(
            by_id.contains_key(id) && accepted.insert(id.to_owned()),
            "C_ACCEPTANCE_UNPROVEN",
        )?;
    }
    let mut rejected = HashSet::new();
    for item in a(response, "rejected") {
        fields(
            item,
            &[
                ("record_id", "uuid"),
                ("status", "int"),
                ("code", "text"),
                ("retryable", "bool"),
            ],
            &[],
        )?;
        let id = s(item, "record_id");
        let status: u16 = n(item, "status")
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        require(
            crate::error_catalogue::server(s(item, "code"))
                == Some((status, item["retryable"].as_bool().ok_or_else(schema)?)),
            "C_SCHEMA_VIOLATION",
        )?;
        require(
            by_id.contains_key(id) && !accepted.contains(id) && rejected.insert(id.to_owned()),
            "C_ACCEPTANCE_UNPROVEN",
        )?;
    }
    require(
        accepted.len() + rejected.len() == by_id.len()
            && by_id
                .keys()
                .all(|id| accepted.contains(id) || rejected.contains(id)),
        "C_ACCEPTANCE_UNPROVEN",
    )?;

    let receipts = a(response, "upload_receipts");
    require(receipts.len() == accepted.len(), "C_ACCEPTANCE_UNPROVEN")?;
    let mut receipt_ids = HashSet::new();
    let mut transparency_indexes = HashSet::new();
    let mut last_record_id: Option<&str> = None;
    for receipt in receipts {
        validate_receipt(receipt, pin)?;
        let object_hash = s(receipt, "object_hash");
        let record_id = by_hash
            .get(object_hash)
            .ok_or(ClientError("C_ACCEPTANCE_UNPROVEN"))?;
        require(accepted.contains(record_id), "C_ACCEPTANCE_UNPROVEN")?;
        require(
            receipt["relay_seq"].is_null()
                && receipt["entry_hash"].is_null()
                && s(receipt, "previous_head_hash") == s(receipt, "resulting_head_hash"),
            "C_ACCEPTANCE_UNPROVEN",
        )?;
        require(
            last_record_id.is_none_or(|previous| previous < record_id.as_str()),
            "C_SCHEMA_VIOLATION",
        )?;
        require(
            receipt_ids.insert(s(receipt, "receipt_id").to_owned())
                && transparency_indexes.insert(n(receipt, "transparency_index")),
            "C_DEVICE_EQUIVOCATION",
        )?;
        last_record_id = Some(record_id);
    }
    Ok(response.clone())
}

/// Validate the §10.12 CompactionRecord publication result and its original log receipt.
pub fn validate_compaction_publish_response(
    response: &Value,
    compaction: &Value,
    pin: &RelayPin,
) -> Result<Value> {
    fields(
        response,
        &[
            ("compaction_id", "uuid"),
            ("receipt", "object"),
            ("upload_receipts", "array"),
        ],
        &[],
    )?;
    wire::validate(compaction, "compaction")?;
    require(
        s(compaction, "chain_id") == pin.chain_id
            && s(compaction, "log_id") == pin.log_id
            && s(response, "compaction_id") == s(compaction, "compaction_id"),
        "C_LOG_HASH_MISMATCH",
    )?;
    let receipts = a(response, "upload_receipts");
    require(receipts.len() == 1, "C_ACCEPTANCE_UNPROVEN")?;
    let entry = &response["receipt"];
    validate_log_entry(entry)?;
    require(
        s(entry, "kind") == "compaction" && entry["object"] == *compaction,
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    verify_receipt(&receipts[0], compaction, Some(entry), pin)?;
    Ok(response.clone())
}

/// Validate the post-delete proof against the previously committed authority and the exact
/// locally submitted root authorization. A valid relay signature alone never authorizes erasure.
pub fn validate_deletion_proof(
    proof: &Value,
    pin: &RelayPin,
    authority: &crate::authority::Authority,
    request: &Value,
) -> Result<Value> {
    pin.validate()?;
    require(
        authority.pin.chain_id == pin.chain_id
            && authority.pin.log_id == pin.log_id
            && authority.pin.origin == pin.origin
            && authority.pin.public_key == pin.public_key,
        "C_RELAY_KEY_MISMATCH",
    )?;
    fields(
        proof,
        &[
            ("ledger", "object"),
            ("parameters", "object"),
            ("epoch", "object"),
            ("enrollment_chain", "array"),
            ("admin_operation", "object?"),
            ("device_authorization", "object?"),
            ("delete_receipt", "object"),
            ("deletion_transparency_entry", "object"),
        ],
        &[],
    )?;
    require(
        !proof["admin_operation"].is_null()
            && proof["device_authorization"].is_null()
            && a(proof, "enrollment_chain").is_empty(),
        "C_DELETION_UNVERIFIED",
    )?;

    let parameters = &proof["parameters"];
    let epoch = &proof["epoch"];
    wire::validate(parameters, "parameters").map_err(|_| schema())?;
    wire::validate(epoch, "epoch").map_err(|_| schema())?;
    let known_parameters = authority
        .parameters
        .as_ref()
        .ok_or(ClientError("C_DELETION_UNVERIFIED"))?;
    let known_epoch = &authority.current_epoch()?.record;
    require(
        parameters == known_parameters && epoch == known_epoch,
        "C_DELETION_UNVERIFIED",
    )?;

    fields(
        request,
        &[
            ("deletion_id", "uuid"),
            ("expected_parameters_hash", "b32"),
            ("expected_epoch_hash", "b32"),
            ("admin_operation", "object"),
        ],
        &[],
    )?;
    require(
        proof["admin_operation"] == request["admin_operation"]
            && s(&request["admin_operation"], "action") == "delete_chain",
        "C_DELETION_UNVERIFIED",
    )?;
    let action_payload = json!({
        "deletion_id": request["deletion_id"],
        "expected_parameters_hash": request["expected_parameters_hash"],
        "expected_epoch_hash": request["expected_epoch_hash"],
    });
    crate::admin_action::verify(
        &request["admin_operation"],
        &action_payload,
        parameters,
        epoch,
    )?;
    require(
        s(&request["admin_operation"], "chain_id") == pin.chain_id
            && authority.admin_public_key == s(parameters, "admin_public_key"),
        "C_DELETION_UNVERIFIED",
    )?;

    let ledger = &proof["ledger"];
    fields(
        ledger,
        &[
            ("chain_id", "b32"),
            ("deletion_id", "uuid"),
            ("authorized_parameters_hash", "b32"),
            ("authorized_epoch_hash", "b32"),
            ("authorization_hash", "b32"),
            ("requested_at_ms", "int"),
            ("completed_at_ms", "int?"),
            ("assurance", "crypto-shred|backup-expiry"),
            ("delete_after_ms", "int?"),
            ("receipt_hash", "b32"),
        ],
        &[],
    )?;
    require(
        n(ledger, "requested_at_ms") > 0
            && (ledger["completed_at_ms"].is_null()
                || n(ledger, "completed_at_ms") >= n(ledger, "requested_at_ms"))
            && (ledger["delete_after_ms"].is_null()
                || n(ledger, "delete_after_ms") >= n(ledger, "requested_at_ms")),
        "C_DELETION_UNVERIFIED",
    )?;
    let mut unsigned_ledger = ledger.clone();
    unsigned_ledger
        .as_object_mut()
        .ok_or_else(schema)?
        .remove("receipt_hash");
    let operation = &request["admin_operation"];
    require(
        s(ledger, "chain_id") == pin.chain_id
            && ledger["deletion_id"] == request["deletion_id"]
            && s(ledger, "authorized_parameters_hash") == object_hash(parameters)
            && s(ledger, "authorized_epoch_hash") == object_hash(epoch)
            && s(ledger, "authorization_hash") == object_hash(operation)
            && s(ledger, "receipt_hash") == object_hash(&unsigned_ledger),
        "C_DELETION_UNVERIFIED",
    )?;

    let receipt = &proof["delete_receipt"];
    fields(
        receipt,
        &[
            ("type", "PALLASYNC-DELETE-RECEIPT-v3"),
            ("version", "3.0"),
            ("chain_id", "b32"),
            ("deletion_id", "uuid"),
            ("log_id", "uuid"),
            ("relay_key_id", "b32"),
            ("authorized_parameters_hash", "b32"),
            ("authorized_epoch_hash", "b32"),
            ("authorization_hash", "b32"),
            ("requested_at_ms", "int"),
            ("completed_at_ms", "int?"),
            ("assurance", "crypto-shred|backup-expiry"),
            ("delete_after_ms", "int?"),
            ("ledger_receipt_hash", "b32"),
            ("transparency_id", "uuid"),
            ("transparency_index", "int"),
            ("transparency_hash", "b32"),
            ("relay_signature", "b64"),
        ],
        &[],
    )?;
    pin.verify(receipt, CTX_DELETE)?;
    require(
        n(receipt, "transparency_index") > 0
            && receipt["chain_id"] == ledger["chain_id"]
            && receipt["deletion_id"] == ledger["deletion_id"]
            && receipt["authorized_parameters_hash"] == ledger["authorized_parameters_hash"]
            && receipt["authorized_epoch_hash"] == ledger["authorized_epoch_hash"]
            && receipt["authorization_hash"] == ledger["authorization_hash"]
            && receipt["requested_at_ms"] == ledger["requested_at_ms"]
            && receipt["completed_at_ms"] == ledger["completed_at_ms"]
            && receipt["assurance"] == ledger["assurance"]
            && receipt["delete_after_ms"] == ledger["delete_after_ms"]
            && receipt["ledger_receipt_hash"] == ledger["receipt_hash"],
        "C_DELETION_UNVERIFIED",
    )?;

    let entry = &proof["deletion_transparency_entry"];
    validate_transparency(entry, pin)?;
    require(
        s(entry, "operation") == "delete"
            // The relay may issue a later signed completion receipt. It updates the
            // minimal ledger hash but keeps the immutable original transparency entry.
            && (!ledger["completed_at_ms"].is_null() || entry["object_hash"] == object_hash(ledger))
            && receipt["transparency_id"] == entry["transparency_id"]
            && receipt["transparency_index"] == entry["entry_index"]
            && receipt["transparency_hash"] == object_hash(entry),
        "C_DELETION_UNVERIFIED",
    )?;
    Ok(proof.clone())
}

/// Validate that a later signed completion receipt is a monotone revision of the
/// same deletion and immutable transparency entry (§7.12.3). The enclosing proof
/// still needs `validate_deletion_proof`; this check never authorizes erasure.
pub fn validate_deletion_receipt_revision(
    previous: &Value,
    next: &Value,
    pin: &RelayPin,
) -> Result<Value> {
    pin.validate()?;
    for receipt in [previous, next] {
        fields(
            receipt,
            &[
                ("type", "PALLASYNC-DELETE-RECEIPT-v3"),
                ("version", "3.0"),
                ("chain_id", "b32"),
                ("deletion_id", "uuid"),
                ("log_id", "uuid"),
                ("relay_key_id", "b32"),
                ("authorized_parameters_hash", "b32"),
                ("authorized_epoch_hash", "b32"),
                ("authorization_hash", "b32"),
                ("requested_at_ms", "int"),
                ("completed_at_ms", "int?"),
                ("assurance", "crypto-shred|backup-expiry"),
                ("delete_after_ms", "int?"),
                ("ledger_receipt_hash", "b32"),
                ("transparency_id", "uuid"),
                ("transparency_index", "int"),
                ("transparency_hash", "b32"),
                ("relay_signature", "b64"),
            ],
            &[],
        )?;
        pin.verify(receipt, CTX_DELETE)?;
    }
    let immutable = [
        "type",
        "version",
        "chain_id",
        "deletion_id",
        "log_id",
        "relay_key_id",
        "authorized_parameters_hash",
        "authorized_epoch_hash",
        "authorization_hash",
        "requested_at_ms",
        "assurance",
        "delete_after_ms",
        "transparency_id",
        "transparency_index",
        "transparency_hash",
    ];
    require(
        immutable
            .iter()
            .all(|field| previous[*field] == next[*field])
            && n(previous, "transparency_index") > 0
            && n(previous, "requested_at_ms") > 0
            && (previous["completed_at_ms"].is_null()
                || n(previous, "completed_at_ms") >= n(previous, "requested_at_ms"))
            && (next["completed_at_ms"].is_null()
                || n(next, "completed_at_ms") >= n(next, "requested_at_ms"))
            && (previous["completed_at_ms"].is_null()
                || (!next["completed_at_ms"].is_null()
                    && n(next, "completed_at_ms") >= n(previous, "completed_at_ms"))),
        "C_DELETION_UNVERIFIED",
    )?;
    Ok(next.clone())
}

/// Validate the optional, unsigned public witness container independently from the
/// immutable relay receipt (§7.12.3/§7.12.5/§17.11). Authority must be a checkpoint
/// already verified from the pinned log; it is never accepted from this archive.
pub fn validate_deletion_evidence_bundle(
    receipt: &Value,
    bundle: &Value,
    authority: &crate::authority::Authority,
) -> Result<Value> {
    authority.pin.validate()?;
    require(
        authority.pin.chain_id == s(receipt, "chain_id")
            && authority.pin.log_id == s(receipt, "log_id"),
        "C_RELAY_KEY_MISMATCH",
    )?;
    fields(
        receipt,
        &[
            ("type", "PALLASYNC-DELETE-RECEIPT-v3"),
            ("version", "3.0"),
            ("chain_id", "b32"),
            ("deletion_id", "uuid"),
            ("log_id", "uuid"),
            ("relay_key_id", "b32"),
            ("authorized_parameters_hash", "b32"),
            ("authorized_epoch_hash", "b32"),
            ("authorization_hash", "b32"),
            ("requested_at_ms", "int"),
            ("completed_at_ms", "int?"),
            ("assurance", "crypto-shred|backup-expiry"),
            ("delete_after_ms", "int?"),
            ("ledger_receipt_hash", "b32"),
            ("transparency_id", "uuid"),
            ("transparency_index", "int"),
            ("transparency_hash", "b32"),
            ("relay_signature", "b64"),
        ],
        &[],
    )?;
    authority.pin.verify(receipt, CTX_DELETE)?;
    require(
        n(receipt, "transparency_index") > 0
            && (receipt["completed_at_ms"].is_null()
                || n(receipt, "completed_at_ms") >= n(receipt, "requested_at_ms"))
            && (receipt["delete_after_ms"].is_null()
                || n(receipt, "delete_after_ms") >= n(receipt, "requested_at_ms")),
        "C_DELETION_UNVERIFIED",
    )?;
    fields(
        bundle,
        &[
            ("type", "PALLASYNC-DELETION-EVIDENCE-v3"),
            ("version", "3.0"),
            ("chain_id", "b32"),
            ("deletion_id", "uuid"),
            ("delete_receipt_hash", "b32"),
            ("witnesses", "array"),
        ],
        &[],
    )?;
    require(a(bundle, "witnesses").len() <= 128, "C_SCHEMA_VIOLATION")?;
    require(
        s(bundle, "chain_id") == s(receipt, "chain_id")
            && bundle["deletion_id"] == receipt["deletion_id"]
            && s(bundle, "delete_receipt_hash") == object_hash(receipt),
        "C_DELETION_UNVERIFIED",
    )?;
    let mut previous: Option<Vec<u8>> = None;
    let authorized_epoch = authority
        .epochs
        .values()
        .find(|epoch| object_hash(&epoch.record) == s(receipt, "authorized_epoch_hash"))
        .ok_or(ClientError("C_DELETION_UNVERIFIED"))?;
    require(
        authority.parameters.as_ref().is_some_and(|parameters| {
            object_hash(parameters) == s(receipt, "authorized_parameters_hash")
        }),
        "C_DELETION_UNVERIFIED",
    )?;
    for witness in a(bundle, "witnesses") {
        fields(
            witness,
            &[
                ("type", "PALLASYNC-DELETION-WITNESS-v3"),
                ("version", "3.0"),
                ("chain_id", "b32"),
                ("deletion_id", "uuid"),
                ("delete_receipt_hash", "b32"),
                ("authorized_parameters_hash", "b32"),
                ("authorized_epoch_hash", "b32"),
                ("authorization_hash", "b32"),
                ("witness_device_id", "uuid"),
                ("created_at_ms", "int"),
                ("signature", "b64"),
            ],
            &[],
        )?;
        let device_id = s(witness, "witness_device_id");
        let id_bytes = uuid_bytes(device_id)?;
        require(
            previous.as_ref().is_none_or(|last| last < &id_bytes),
            "C_DELETION_UNVERIFIED",
        )?;
        previous = Some(id_bytes);
        require(
            witness["chain_id"] == receipt["chain_id"]
                && witness["deletion_id"] == receipt["deletion_id"]
                && s(witness, "delete_receipt_hash") == object_hash(receipt)
                && witness["authorized_parameters_hash"] == receipt["authorized_parameters_hash"]
                && witness["authorized_epoch_hash"] == receipt["authorized_epoch_hash"]
                && witness["authorization_hash"] == receipt["authorization_hash"]
                && a(&authorized_epoch.record, "members")
                    .iter()
                    .any(|member| member == device_id),
            "C_DELETION_UNVERIFIED",
        )?;
        let admission = authority
            .devices
            .get(device_id)
            .ok_or(ClientError("C_DELETION_UNVERIFIED"))?;
        require(
            n(&admission.record, "generation") == n(&authorized_epoch.record, "generation")
                && admission.enrolled_seq < authorized_epoch.relay_seq
                && admission
                    .revoked_seq
                    .is_none_or(|seq| seq > authorized_epoch.relay_seq),
            "C_DELETION_UNVERIFIED",
        )?;
        require(
            verify_signed_json(
                s(&admission.record, "device_public_key"),
                s(witness, "signature"),
                witness,
                CTX_DELETION_WITNESS,
            )
            .unwrap_or(false),
            "C_SIGNATURE_INVALID",
        )?;
    }
    Ok(bundle.clone())
}

/// Sign one explicitly confirmed local intent witness using this enrolled device's key.
/// The caller must show the exact receipt and obtain foreground confirmation first.
pub fn create_deletion_witness(
    receipt: &Value,
    authority: &crate::authority::Authority,
    device_id: &str,
    signing_seed: &mut [u8],
    created_at_ms: u64,
) -> Result<Value> {
    require(
        signing_seed.len() == 32 && created_at_ms > 0 && created_at_ms <= jcs::MAX_INTEGER,
        "C_SCHEMA_VIOLATION",
    )?;
    authority.pin.validate()?;
    fields(
        receipt,
        &[
            ("type", "PALLASYNC-DELETE-RECEIPT-v3"),
            ("version", "3.0"),
            ("chain_id", "b32"),
            ("deletion_id", "uuid"),
            ("log_id", "uuid"),
            ("relay_key_id", "b32"),
            ("authorized_parameters_hash", "b32"),
            ("authorized_epoch_hash", "b32"),
            ("authorization_hash", "b32"),
            ("requested_at_ms", "int"),
            ("completed_at_ms", "int?"),
            ("assurance", "crypto-shred|backup-expiry"),
            ("delete_after_ms", "int?"),
            ("ledger_receipt_hash", "b32"),
            ("transparency_id", "uuid"),
            ("transparency_index", "int"),
            ("transparency_hash", "b32"),
            ("relay_signature", "b64"),
        ],
        &[],
    )?;
    authority.pin.verify(receipt, CTX_DELETE)?;
    require(
        s(receipt, "chain_id") == authority.pin.chain_id,
        "C_RELAY_KEY_MISMATCH",
    )?;
    let epoch = authority
        .epochs
        .values()
        .find(|epoch| object_hash(&epoch.record) == s(receipt, "authorized_epoch_hash"))
        .ok_or(ClientError("C_DELETION_UNVERIFIED"))?;
    let parameters = authority
        .parameters
        .as_ref()
        .ok_or(ClientError("C_DELETION_UNVERIFIED"))?;
    require(
        object_hash(parameters) == s(receipt, "authorized_parameters_hash")
            && a(&epoch.record, "members")
                .iter()
                .any(|member| member == device_id),
        "C_DELETION_UNVERIFIED",
    )?;
    let admission = authority
        .devices
        .get(device_id)
        .ok_or(ClientError("C_DELETION_UNVERIFIED"))?;
    require(
        n(&admission.record, "generation") == n(&epoch.record, "generation")
            && admission.enrolled_seq < epoch.relay_seq
            && admission
                .revoked_seq
                .is_none_or(|seq| seq > epoch.relay_seq),
        "C_DELETION_UNVERIFIED",
    )?;
    let device_public_key = s(&admission.record, "device_public_key");
    let mut seed: [u8; 32] = signing_seed.try_into().map_err(|_| schema())?;
    let signing_key = ed25519_dalek::SigningKey::from_bytes(&seed);
    use zeroize::Zeroize;
    seed.zeroize();
    signing_seed.zeroize();
    require(
        signing_key.verifying_key().as_bytes()
            == decode(device_public_key, Some(32))
                .map_err(|_| schema())?
                .as_slice(),
        "C_DEVICE_KEY_MISMATCH",
    )?;
    let mut witness = json!({
        "type":"PALLASYNC-DELETION-WITNESS-v3","version":"3.0",
        "chain_id":s(receipt,"chain_id"),"deletion_id":s(receipt,"deletion_id"),
        "delete_receipt_hash":object_hash(receipt),
        "authorized_parameters_hash":s(receipt,"authorized_parameters_hash"),
        "authorized_epoch_hash":s(receipt,"authorized_epoch_hash"),
        "authorization_hash":s(receipt,"authorization_hash"),
        "witness_device_id":device_id,"created_at_ms":created_at_ms,"signature":""
    });
    let canonical = jcs::canonicalize_json(&signing_value_without_member(&witness, "signature"))
        .map_err(|_| schema())?;
    witness["signature"] = json!(crate::crypto::sign_with_context(
        &signing_key,
        CTX_DELETION_WITNESS,
        &canonical,
    ));
    require(
        verify_signed_json(
            device_public_key,
            s(&witness, "signature"),
            &witness,
            CTX_DELETION_WITNESS,
        )
        .unwrap_or(false),
        "C_SIGNATURE_INVALID",
    )?;
    Ok(witness)
}

fn signing_value_without_member(value: &Value, member: &str) -> Value {
    let mut unsigned = value.clone();
    if let Some(fields) = unsigned.as_object_mut() {
        fields.remove(member);
    }
    unsigned
}

fn uuid_bytes(value: &str) -> Result<Vec<u8>> {
    require(wire::uuid(value), "C_SCHEMA_VIOLATION")?;
    let compact = value
        .bytes()
        .filter(|byte| *byte != b'-')
        .collect::<Vec<_>>();
    let decoded = hex::decode(compact).map_err(|_| schema())?;
    require(decoded.len() == 16, "C_SCHEMA_VIOLATION")?;
    Ok(decoded)
}
/// Validates the exact submitted object and the complete original LogEntry.
/// Inclusion in a pinned prefix is a separate operation; callers retain this evidence.
pub fn verify_receipt(
    receipt: &Value,
    object: &Value,
    entry: Option<&Value>,
    pin: &RelayPin,
) -> Result {
    validate_receipt(receipt, pin)?;
    require(
        s(receipt, "object_hash") == object_hash(object) && s(object, "chain_id") == pin.chain_id,
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    match entry {
        Some(entry) => {
            validate_log_entry(entry)?;
            require(
                entry["object"] == *object
                    && receipt["relay_seq"] == entry["relay_seq"]
                    && receipt["received_at_ms"] == entry["received_at_ms"]
                    && s(receipt, "entry_hash") == object_hash(entry),
                "C_ACCEPTANCE_UNPROVEN",
            )?;
            require(
                data_link(s(receipt, "previous_head_hash"), entry)?
                    == s(receipt, "resulting_head_hash"),
                "C_LOG_HASH_MISMATCH",
            )
        }
        None => {
            wire::validate(object, "record")?;
            require(
                receipt["relay_seq"].is_null()
                    && receipt["entry_hash"].is_null()
                    && receipt["previous_head_hash"] == receipt["resulting_head_hash"],
                "C_ACCEPTANCE_UNPROVEN",
            )
        }
    }
}
pub fn validate_transparency(entry: &Value, pin: &RelayPin) -> Result {
    fields(
        entry,
        &[
            ("type", "PALLASYNC-TRANSPARENCY-ENTRY-v3"),
            ("version", "3.0"),
            ("transparency_id", "uuid"),
            ("entry_index", "int"),
            ("chain_id", "b32"),
            ("log_id", "uuid"),
            ("relay_key_id", "b32"),
            ("operation", "accept|reject|prune|delete|migrate"),
            ("object_hash", "b32"),
            ("relay_seq", "int?"),
            ("resulting_head_hash", "b32"),
            ("previous_transparency_hash", "b32"),
            ("timestamp", "int"),
            ("code", "string?"),
            ("relay_signature", "b64"),
        ],
        &[],
    )?;
    require(
        n(entry, "entry_index") > 0 && (entry["relay_seq"].is_null() || n(entry, "relay_seq") > 0),
        "C_TRANSPARENCY_INVALID",
    )?;
    match s(entry, "operation") {
        "accept" => require(entry["code"].is_null(), "C_TRANSPARENCY_INVALID")?,
        "reject" => require(
            entry["relay_seq"].is_null()
                && crate::error_catalogue::known(s(entry, "code"))
                && !s(entry, "code").starts_with("C_"),
            "C_TRANSPARENCY_INVALID",
        )?,
        _ => require(
            entry["relay_seq"].is_null() && entry["code"].is_null(),
            "C_TRANSPARENCY_INVALID",
        )?,
    }
    pin.verify(entry, CTX_TRANSPARENCY)
}
pub fn transparency_link(previous: &str, entry: &Value, pin: &RelayPin) -> Result<String> {
    validate_transparency(entry, pin)?;
    require(
        s(entry, "previous_transparency_hash") == previous,
        "C_TRANSPARENCY_INVALID",
    )?;
    link(
        previous,
        n(entry, "entry_index"),
        &object_hash(entry),
        b"PALLASYNC-TRANSPARENCY-LINK-v3\0",
    )
}
pub fn verify_receipt_transparency(receipt: &Value, entry: &Value, pin: &RelayPin) -> Result {
    validate_receipt(receipt, pin)?;
    validate_transparency(entry, pin)?;
    for (r, e) in [
        ("transparency_id", "transparency_id"),
        ("transparency_index", "entry_index"),
        ("object_hash", "object_hash"),
        ("relay_seq", "relay_seq"),
        ("resulting_head_hash", "resulting_head_hash"),
    ] {
        require(receipt[r] == entry[e], "C_TRANSPARENCY_INVALID")?;
    }
    require(
        s(entry, "operation") == "accept"
            && transparency_link(s(entry, "previous_transparency_hash"), entry, pin)?
                == s(receipt, "transparency_hash"),
        "C_TRANSPARENCY_INVALID",
    )
}
pub fn validate_head(head: &Value, pin: &RelayPin) -> Result {
    fields(
        head,
        &[
            ("type", "PALLASYNC-TRANSPARENCY-HEAD-v3"),
            ("version", "3.0"),
            ("chain_id", "b32"),
            ("log_id", "uuid"),
            ("relay_key_id", "b32"),
            ("transparency_id", "uuid"),
            ("entry_index", "int"),
            ("transparency_hash", "b32"),
            ("head_seq", "int"),
            ("head_hash", "b32"),
            ("parameters_hash", "b32"),
            ("epoch_hash", "b32"),
            ("min_available_seq", "int"),
            ("timestamp", "int"),
            ("relay_signature", "b64"),
        ],
        &[],
    )?;
    pin.verify(head, CTX_HEAD)?;
    require(
        n(head, "min_available_seq") > 0
            && n(head, "min_available_seq") <= n(head, "head_seq").saturating_add(1),
        "C_TRANSPARENCY_INVALID",
    )?;
    if n(head, "entry_index") == 0 {
        require(
            s(head, "transparency_hash")
                == transparency_zero(&pin.chain_id, s(head, "transparency_id"), &pin.key_id()?)?,
            "C_TRANSPARENCY_INVALID",
        )?;
    }
    if n(head, "head_seq") == 0 {
        require(
            s(head, "head_hash") == data_zero(&pin.chain_id, &pin.log_id)?,
            "C_LOG_HASH_MISMATCH",
        )?;
    }
    Ok(())
}
#[derive(Clone, Debug, Deserialize, Serialize, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct TransparencyAnchor {
    pub transparency_id: String,
    pub index: u64,
    pub hash: String,
}
/// At most one page in RAM. The caller atomically saves entries and the returned anchor.
/// An empty/missing suffix is pending, never a verified advance.
pub fn verify_transparency_page(
    anchor: &TransparencyAnchor,
    entries: &[Value],
    target: &Value,
    pin: &RelayPin,
) -> Result<TransparencyAnchor> {
    validate_head(target, pin)?;
    require(
        anchor.index <= jcs::MAX_INTEGER
            && decode(&anchor.hash, Some(32)).is_ok()
            && anchor.transparency_id == s(target, "transparency_id")
            && anchor.index <= n(target, "entry_index"),
        "C_TRANSPARENCY_INVALID",
    )?;
    require(
        entries.len() <= 1000
            && jcs::canonicalize_json(&Value::Array(entries.to_vec()))
                .map_err(|_| schema())?
                .len()
                <= 8_388_608,
        "C_SCHEMA_VIOLATION",
    )?;
    if anchor.index == 0 {
        require(
            anchor.hash
                == transparency_zero(&pin.chain_id, &anchor.transparency_id, &pin.key_id()?)?,
            "C_TRANSPARENCY_INVALID",
        )?;
    }
    if entries.is_empty() && anchor.index < n(target, "entry_index") {
        return Err(ClientError("C_MISSING_DEPENDENCY"));
    }
    let mut next = anchor.clone();
    for entry in entries {
        require(
            n(entry, "entry_index") == next.index + 1
                && n(entry, "entry_index") <= n(target, "entry_index")
                && s(entry, "transparency_id") == next.transparency_id,
            "C_TRANSPARENCY_INVALID",
        )?;
        next.hash = transparency_link(&next.hash, entry, pin)?;
        next.index += 1;
    }
    if next.index == n(target, "entry_index") {
        require(
            next.hash == s(target, "transparency_hash"),
            "C_TRANSPARENCY_INVALID",
        )?;
    }
    Ok(next)
}

/// §10.6: every accepted ID requires one original, signed receipt. Rejected objects stay local.
pub fn verify_record_upload_response(
    response: &Value,
    submitted: &[Value],
    pin: &RelayPin,
) -> Result {
    fields(
        response,
        &[
            ("accepted", "array"),
            ("rejected", "array"),
            ("upload_receipts", "array"),
            ("generation", "u32"),
            ("log_id", "uuid"),
            ("head_seq", "int"),
            ("head_hash", "b32"),
        ],
        &[],
    )?;
    pin.validate()?;
    require(s(response, "log_id") == pin.log_id, "C_LOG_HASH_MISMATCH")?;
    require(
        !submitted.is_empty() && submitted.len() <= 64,
        "C_SCHEMA_VIOLATION",
    )?;
    let mut objects = BTreeMap::new();
    for object in submitted {
        wire::validate(object, "record")?;
        require(
            s(object, "chain_id") == pin.chain_id
                && object["generation"] == response["generation"]
                && objects.insert(s(object, "record_id"), object).is_none(),
            "C_SCHEMA_VIOLATION",
        )?;
    }
    let mut receipts = BTreeMap::new();
    let mut receipt_ids = HashSet::new();
    for receipt in a(response, "upload_receipts") {
        validate_receipt(receipt, pin)?;
        require(
            receipts
                .insert(s(receipt, "object_hash"), receipt)
                .is_none()
                && receipt_ids.insert(s(receipt, "receipt_id")),
            "C_ACCEPTANCE_UNPROVEN",
        )?;
    }
    require(
        receipts.len() == a(response, "accepted").len(),
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    let mut accounted = HashSet::new();
    for item in a(response, "accepted") {
        fields(
            item,
            &[
                ("record_id", "uuid"),
                ("duplicate", "bool"),
                ("receipt", "object"),
            ],
            &[],
        )?;
        let object = objects
            .get(s(item, "record_id"))
            .ok_or(ClientError("C_ACCEPTANCE_UNPROVEN"))?;
        let receipt = receipts
            .get(object_hash(object).as_str())
            .ok_or(ClientError("C_ACCEPTANCE_UNPROVEN"))?;
        require(
            accounted.insert(s(item, "record_id"))
                && s(&item["receipt"], "kind") == "record"
                && n(receipt, "relay_seq") <= n(response, "head_seq"),
            "C_ACCEPTANCE_UNPROVEN",
        )?;
        verify_receipt(receipt, object, Some(&item["receipt"]), pin)?;
    }
    for item in a(response, "rejected") {
        fields(
            item,
            &[
                ("record_id", "uuid"),
                ("status", "int"),
                ("code", "string"),
                ("retryable", "bool"),
            ],
            &[],
        )?;
        require(
            objects.contains_key(s(item, "record_id"))
                && accounted.insert(s(item, "record_id"))
                && crate::error_catalogue::server(s(item, "code")).is_some_and(
                    |(status, retryable)| {
                        n(item, "status") == u64::from(status)
                            && item["retryable"].as_bool() == Some(retryable)
                    },
                ),
            "C_SCHEMA_VIOLATION",
        )?;
    }
    require(accounted.len() == submitted.len(), "C_ACCEPTANCE_UNPROVEN")
}

/// §10.13 repair responses retain the ordinary per-record receipt checks and add one
/// required lifecycle bit. Do not let that extra member weaken the §10.6 exact schema.
pub fn verify_record_repair_response(
    response: &Value,
    submitted: &[Value],
    pin: &RelayPin,
) -> Result<bool> {
    require(
        response
            .get("repair_required")
            .and_then(Value::as_bool)
            .is_some(),
        "C_SCHEMA_VIOLATION",
    )?;
    let mut ordinary = response.clone();
    let repair_required = ordinary
        .as_object_mut()
        .and_then(|object| object.remove("repair_required"))
        .and_then(|value| value.as_bool())
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
    verify_record_upload_response(&ordinary, submitted, pin)?;
    Ok(repair_required)
}

/// §10.2: establish H0..H5 from the locally authorized genesis and all five signed receipts.
/// This deliberately leaves transparency at T0 until its complete signed suffix is fetched.
pub fn verify_genesis_acceptance(
    response: &Value,
    genesis: &Value,
    identity: &Value,
    origin: &str,
    relay_key: &str,
) -> Result<Value> {
    fields(
        identity,
        &[("chain_id", "b32"), ("admin_public_key", "b32")],
        &[],
    )?;
    fields(
        genesis,
        &[
            ("parameters", "object"),
            ("device_record", "object"),
            ("epoch", "object"),
            ("envelopes", "array"),
        ],
        &[],
    )?;
    fields(
        response,
        &[
            ("parameters_hash", "b32"),
            ("epoch_hash", "b32"),
            ("log_id", "uuid"),
            ("head_seq", "int"),
            ("head_hash", "b32"),
            ("device_status", "active"),
            ("upload_receipts", "array"),
        ],
        &[],
    )?;
    let p = &genesis["parameters"];
    let d = &genesis["device_record"];
    let e = &genesis["epoch"];
    for (v, kind) in [(p, "parameters"), (d, "device"), (e, "epoch")] {
        wire::validate(v, kind)?;
        require(
            v["chain_id"] == identity["chain_id"] && n(v, "generation") == 0,
            "C_SCHEMA_VIOLATION",
        )?;
    }
    require(
        p["admin_public_key"] == identity["admin_public_key"]
            && s(&d["enrollment"], "signer_kind") == "admin"
            && d["enrollment"]["request_hash"].is_null(),
        "C_SIGNATURE_INVALID",
    )?;
    wire::verify(p, s(identity, "admin_public_key"), CTX_PARAMETERS)?;
    wire::verify(
        &d["enrollment"],
        s(identity, "admin_public_key"),
        CTX_ENROLLMENT,
    )?;
    wire::verify(d, s(d, "device_public_key"), CTX_DEVICE_RECORD)?;
    wire::verify(e, s(identity, "admin_public_key"), CTX_EPOCH)?;
    require(
        n(e, "epoch") == 0
            && n(d, "meta_epoch") == 0
            && s(e, "reason") == "genesis"
            && s(e, "signer_kind") == "admin"
            && a(e, "members") == std::slice::from_ref(&d["device_id"])
            && a(e, "revoked_device_ids").is_empty(),
        "C_SCHEMA_VIOLATION",
    )?;
    require(a(genesis, "envelopes").len() == 2, "C_SCHEMA_VIOLATION")?;
    let mut device_envelope = None;
    let mut recovery_envelope = None;
    for envelope in a(genesis, "envelopes") {
        wire::validate(envelope, "envelope")?;
        for name in [
            "chain_id",
            "generation",
            "epoch",
            "previous_epoch_hash",
            "epoch_commitment",
        ] {
            require(envelope[name] == e[name], "C_SCHEMA_VIOLATION")?;
        }
        let key = if s(envelope, "signer_kind") == "admin" {
            s(identity, "admin_public_key")
        } else {
            require(
                envelope["signer_device_id"] == d["device_id"],
                "C_SIGNATURE_INVALID",
            )?;
            s(d, "device_public_key")
        };
        wire::verify(envelope, key, CTX_ENVELOPE)?;
        if s(envelope, "recipient_kind") == "device" {
            require(
                device_envelope.is_none()
                    && envelope["recipient_device_id"] == d["device_id"]
                    && s(envelope, "recipient_key_hash")
                        == hash_bytes(
                            &decode(s(d, "device_kex_public_key"), Some(32))
                                .map_err(|_| schema())?,
                        ),
                "C_SCHEMA_VIOLATION",
            )?;
            device_envelope = Some(envelope);
        } else {
            require(recovery_envelope.is_none(), "C_SCHEMA_VIOLATION")?;
            recovery_envelope = Some(envelope);
        }
    }
    let device_envelope = device_envelope.ok_or_else(schema)?;
    let recovery_envelope = recovery_envelope.ok_or_else(schema)?;
    require(
        device_envelope["envelope_id"] != recovery_envelope["envelope_id"]
            && s(e, "recovery_envelope_hash") == object_hash(recovery_envelope),
        "C_SCHEMA_VIOLATION",
    )?;
    require(
        s(response, "parameters_hash") == object_hash(p)
            && s(response, "epoch_hash") == object_hash(e)
            && n(response, "head_seq") == 5
            && a(response, "upload_receipts").len() == 5,
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    let pin = RelayPin {
        origin: origin.into(),
        chain_id: s(identity, "chain_id").into(),
        public_key: relay_key.into(),
        log_id: s(response, "log_id").into(),
    };
    pin.validate()?;
    let mut receipts = a(response, "upload_receipts").iter().collect::<Vec<_>>();
    receipts.sort_by_key(|r| n(r, "relay_seq"));
    let mut hash = data_zero(&pin.chain_id, &pin.log_id)?;
    let mut transparency_id = String::new();
    for (offset, (kind, object)) in [
        ("parameters", p),
        ("device", d),
        ("envelope", device_envelope),
        ("envelope", recovery_envelope),
        ("epoch", e),
    ]
    .into_iter()
    .enumerate()
    {
        let receipt = receipts[offset];
        let seq = offset as u64 + 1;
        require(
            n(receipt, "relay_seq") == seq
                && s(receipt, "previous_head_hash") == hash
                && n(receipt, "transparency_index") == seq,
            "C_LOG_HASH_MISMATCH",
        )?;
        if offset == 0 {
            transparency_id = s(receipt, "transparency_id").into();
        }
        require(
            s(receipt, "transparency_id") == transparency_id,
            "C_TRANSPARENCY_INVALID",
        )?;
        let mut entry = json!({"relay_seq":seq,"received_at_ms":receipt["received_at_ms"],"kind":kind,"object":object});
        if kind == "epoch" {
            entry["committed_at_ms"] = receipt["received_at_ms"].clone();
        }
        verify_receipt(receipt, object, Some(&entry), &pin)?;
        hash = s(receipt, "resulting_head_hash").into();
    }
    require(hash == s(response, "head_hash"), "C_LOG_HASH_MISMATCH")?;
    Ok(
        json!({"log_id":pin.log_id,"head_seq":5,"head_hash":hash,"parameters_hash":response["parameters_hash"],"epoch_hash":response["epoch_hash"],"transparency_id":transparency_id,"transparency_index":0,"transparency_hash":transparency_zero(&pin.chain_id,&transparency_id,&pin.key_id()?)?}),
    )
}

/// Verify the exact ordered data-log receipts returned by atomic §17.5 re-seed commit.
/// Transparency receipt fields are exposed only as a target; callers must verify the
/// complete suffix from their retained anchor before advancing that anchor.
pub fn verify_reseed_commit_acceptance(
    response: &Value,
    payload: &Value,
    admin_operation: &Value,
    current_parameters: &Value,
    current_epoch: &Value,
    expected_entries: &[Value],
    pin: &RelayPin,
) -> Result<Value> {
    fields(
        response,
        &[
            ("parameters_hash", "b32"),
            ("epoch_hash", "b32"),
            ("log_id", "uuid"),
            ("head_seq", "int"),
            ("head_hash", "b32"),
            ("upload_receipts", "array"),
        ],
        &[],
    )?;
    require(
        response.as_object().is_some_and(|object| object.len() == 6)
            && pin.chain_id == s(&payload["parameters"], "chain_id")
            && s(response, "log_id") == pin.log_id
            && s(response, "parameters_hash") == object_hash(&payload["parameters"])
            && s(response, "epoch_hash") == object_hash(&payload["epoch"])
            && n(&payload["parameters"], "generation") == n(current_parameters, "generation") + 1,
        "C_ACCEPTANCE_UNPROVEN",
    )?;
    crate::admin_action::verify(admin_operation, payload, current_parameters, current_epoch)?;
    require(
        !expected_entries.is_empty()
            && expected_entries.len() == a(response, "upload_receipts").len()
            && n(response, "head_seq") == expected_entries.len() as u64,
        "C_ACCEPTANCE_UNPROVEN",
    )?;

    let mut data_hash = data_zero(&pin.chain_id, &pin.log_id)?;
    let mut transparency_id: Option<&str> = None;
    let mut transparency_index = 0;
    let mut transparency_hash = String::new();
    let receipts = a(response, "upload_receipts");
    for (offset, (expected, receipt)) in expected_entries.iter().zip(receipts).enumerate() {
        fields(expected, &[("kind", "string"), ("object", "object")], &[])?;
        let kind = s(expected, "kind");
        let object = &expected["object"];
        wire::validate(object, kind)?;
        require(
            s(object, "chain_id") == pin.chain_id
                && s(receipt, "object_hash") == object_hash(object)
                && n(receipt, "relay_seq") == offset as u64 + 1
                && s(receipt, "previous_head_hash") == data_hash,
            "C_ACCEPTANCE_UNPROVEN",
        )?;
        validate_receipt(receipt, pin)?;
        match transparency_id {
            None => transparency_id = Some(s(receipt, "transparency_id")),
            Some(id) => require(
                id == s(receipt, "transparency_id")
                    && n(receipt, "transparency_index") > transparency_index,
                "C_TRANSPARENCY_INVALID",
            )?,
        }
        let mut entry = json!({
            "relay_seq": offset as u64 + 1,
            "received_at_ms": receipt["received_at_ms"],
            "kind": kind,
            "object": object,
        });
        if kind == "epoch" {
            entry["committed_at_ms"] = receipt["received_at_ms"].clone();
        }
        verify_receipt(receipt, object, Some(&entry), pin)?;
        data_hash = s(receipt, "resulting_head_hash").into();
        transparency_index = n(receipt, "transparency_index");
        transparency_hash = s(receipt, "transparency_hash").into();
    }
    require(data_hash == s(response, "head_hash"), "C_LOG_HASH_MISMATCH")?;
    Ok(json!({
        "log_id":pin.log_id,
        "head_seq":n(response,"head_seq"),
        "head_hash":data_hash,
        "parameters_hash":s(response,"parameters_hash"),
        "epoch_hash":s(response,"epoch_hash"),
        "generation":n(&payload["parameters"],"generation"),
        "transparency_target_id":transparency_id.ok_or_else(schema)?,
        "transparency_target_index":transparency_index,
        "transparency_target_hash":transparency_hash,
        "receipts":receipts,
    }))
}
