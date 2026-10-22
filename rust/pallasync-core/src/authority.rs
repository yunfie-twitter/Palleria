//! Client authority at a verified data-log position (§7.4/§13/§18.4).
//! Unsigned registry status and wall-clock timestamps never establish eligibility.
use crate::{
    admin_action,
    attestations::{self, RelayPin},
    client_error::{ClientError, Result},
    public_wire::{self as wire, a, n, s},
    strict::verify::*,
};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::{BTreeMap, HashSet};

fn require(ok: bool, code: &'static str) -> Result {
    if ok { Ok(()) } else { Err(ClientError(code)) }
}
#[derive(Clone, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct Admission {
    pub record: Value,
    pub enrolled_seq: u64,
    pub revoked_seq: Option<u64>,
}
#[derive(Clone, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct AcceptedEpoch {
    pub record: Value,
    pub relay_seq: u64,
    pub committed_at_ms: u64,
}
#[derive(Clone, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct ControlObject {
    pub kind: String,
    pub object: Value,
}
/// An internal protected checkpoint, not a REST or signed wire object. Caller persists
/// the new value with all evidence in one transaction; failed steps leave self intact.
#[derive(Clone, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct Authority {
    pub pin: RelayPin,
    pub admin_public_key: String,
    pub sequence: u64,
    pub head_hash: String,
    pub parameters: Option<Value>,
    pub devices: BTreeMap<String, Admission>,
    pub epochs: BTreeMap<u32, AcceptedEpoch>,
    pub staged_envelopes: BTreeMap<String, Value>,
    #[serde(default)]
    pub control_batch: Vec<ControlObject>,
    #[serde(default)]
    pub pending_admin: Option<Value>,
    #[serde(default)]
    pub admin_operations: BTreeMap<String, String>,
    #[serde(default)]
    pub action_payloads: BTreeMap<String, Value>,
}
impl Authority {
    pub fn from_genesis(
        response: &Value,
        genesis: &Value,
        identity: &Value,
        origin: &str,
        relay_key: &str,
    ) -> Result<Self> {
        attestations::verify_genesis_acceptance(response, genesis, identity, origin, relay_key)?;
        let pin = RelayPin {
            origin: origin.into(),
            chain_id: s(identity, "chain_id").into(),
            public_key: relay_key.into(),
            log_id: s(response, "log_id").into(),
        };
        let mut state = Self::new(pin, s(identity, "admin_public_key").into())?;
        let device = a(genesis, "envelopes")
            .iter()
            .find(|v| v["recipient_kind"] == "device")
            .ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let recovery = a(genesis, "envelopes")
            .iter()
            .find(|v| v["recipient_kind"] == "recovery")
            .ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let mut receipts = a(response, "upload_receipts").iter().collect::<Vec<_>>();
        receipts.sort_by_key(|v| n(v, "relay_seq"));
        for (index, (kind, object)) in [
            ("parameters", &genesis["parameters"]),
            ("device", &genesis["device_record"]),
            ("envelope", device),
            ("envelope", recovery),
            ("epoch", &genesis["epoch"]),
        ]
        .iter()
        .enumerate()
        {
            let receipt = receipts[index];
            let mut entry = serde_json::json!({"relay_seq":index+1,"received_at_ms":receipt["received_at_ms"],"kind":kind,"object":object});
            if *kind == "epoch" {
                entry["committed_at_ms"] = receipt["received_at_ms"].clone();
            }
            state = state.advance(&entry, receipt)?;
        }
        Ok(state)
    }
    /// Only an independently selected source plus a locally derived/peer-pinned admin key
    /// may call this constructor. Restoring a live chain must load its existing checkpoint.
    pub fn new(pin: RelayPin, admin_public_key: String) -> Result<Self> {
        pin.validate()?;
        require(
            valid_ed_key(&admin_public_key),
            "C_BOOTSTRAP_TRUST_REQUIRED",
        )?;
        let head_hash = attestations::data_zero(&pin.chain_id, &pin.log_id)?;
        Ok(Self {
            pin,
            admin_public_key,
            sequence: 0,
            head_hash,
            parameters: None,
            devices: BTreeMap::new(),
            epochs: BTreeMap::new(),
            staged_envelopes: BTreeMap::new(),
            control_batch: Vec::new(),
            pending_admin: None,
            admin_operations: BTreeMap::new(),
            action_payloads: BTreeMap::new(),
        })
    }
    fn policy(&self, key: &str) -> bool {
        self.parameters
            .as_ref()
            .is_some_and(|p| p["policy"][key] == true)
    }
    pub fn generation(&self) -> Result<u32> {
        self.parameters
            .as_ref()
            .map(|p| n(p, "generation") as u32)
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))
    }
    pub fn current_epoch(&self) -> Result<&AcceptedEpoch> {
        self.epochs
            .last_key_value()
            .map(|(_, e)| e)
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))
    }
    /// Recheck the exact current epoch against its authorized signer at its acceptance position.
    /// This supports device-signed rotations without treating them as root-signed records.
    pub fn verify_current_epoch(&self, expected: &Value) -> Result {
        let accepted = self.current_epoch()?;
        require(accepted.record == *expected, "C_EPOCH_FORK")?;
        wire::validate(expected, "epoch").map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let signer = self.signer_key(expected, accepted.relay_seq)?;
        Self::signature(expected, signer, CTX_EPOCH)
    }
    fn subject(&self, id: &str, generation: u32) -> Result<&Admission> {
        let d = self
            .devices
            .get(id)
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
        require(
            n(&d.record, "generation") == u64::from(generation),
            "C_MISSING_DEPENDENCY",
        )?;
        Ok(d)
    }
    fn eligible_key(&self, id: &str, generation: u32, at: u64) -> Result<&str> {
        let d = self.subject(id, generation)?;
        require(
            d.enrolled_seq < at && d.revoked_seq.is_none_or(|r| at < r),
            "C_ACCEPTANCE_UNPROVEN",
        )?;
        Ok(s(&d.record, "device_public_key"))
    }
    fn signer_key(&self, value: &Value, at: u64) -> Result<&str> {
        if value["signer_kind"] == "admin" {
            Ok(&self.admin_public_key)
        } else {
            self.eligible_key(
                s(value, "signer_device_id"),
                n(value, "generation") as u32,
                at,
            )
        }
    }
    fn signature(value: &Value, key: &str, ctx: &[u8]) -> Result {
        require(
            verify_signed_json(key, s(value, "signature"), value, ctx).unwrap_or(false),
            "C_SIGNATURE_INVALID",
        )
    }
    /// An authenticated cut cannot end halfway through an administrative commit.
    /// Peer enrollment is authorized by its certificate; it still needs the current
    /// subject envelope. An admin certificate after genesis also needs §7.10's op.
    pub fn complete_cut(&self) -> Result {
        require(
            self.pending_admin.is_none() && self.staged_envelopes.is_empty(),
            "C_MISSING_DEPENDENCY",
        )?;
        for item in self.control_batch.iter().filter(|v| v.kind == "device") {
            require(
                item.object["enrollment"]["signer_kind"] == "device",
                "C_MISSING_DEPENDENCY",
            )?;
            let current = &self.current_epoch()?.record;
            require(
                self.control_batch.iter().any(|e| {
                    e.kind == "envelope"
                        && e.object["recipient_kind"] == "device"
                        && e.object["recipient_device_id"] == item.object["device_id"]
                        && e.object["epoch"] == current["epoch"]
                }),
                "C_MISSING_DEPENDENCY",
            )?;
        }
        Ok(())
    }
    /// Retain the exact public payload from a locally held management request or
    /// authenticated media. Its hash/signature/CAS and equality with log objects are
    /// checked at use; this method grants no authority and accepts no private artifact.
    pub fn with_action_payload(&self, operation: &Value, payload: &Value) -> Result<Self> {
        wire::validate(operation, "admin").map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        admin_action::schema(s(operation, "action"), payload)?;
        Self::signature(operation, &self.admin_public_key, CTX_ADMIN_OP)?;
        require(
            s(operation, "chain_id") == self.pin.chain_id
                && s(operation, "target_hash") == object_hash(payload),
            "C_SCHEMA_VIOLATION",
        )?;
        let mut next = self.clone();
        next.action_payloads
            .insert(object_hash(payload), payload.clone());
        Ok(next)
    }
    fn target(&self, operation: &Value, inferred: Value) -> Result<Value> {
        let payload = self
            .action_payloads
            .get(s(operation, "target_hash"))
            .unwrap_or(&inferred);
        if payload == &inferred {
            require(
                s(operation, "target_hash") == object_hash(payload),
                "C_MISSING_DEPENDENCY",
            )?;
        } else {
            for field in ["device_record", "epoch", "parameters", "compaction"] {
                require(
                    payload.get(field) == inferred.get(field),
                    "C_SCHEMA_VIOLATION",
                )?;
            }
            for field in ["envelopes", "device_records"] {
                require(
                    admin_action::same_objects(a(payload, field), a(&inferred, field)),
                    "C_SCHEMA_VIOLATION",
                )?;
            }
        }
        admin_action::verify(
            operation,
            payload,
            self.parameters
                .as_ref()
                .ok_or(ClientError("C_MISSING_DEPENDENCY"))?,
            &self.current_epoch()?.record,
        )?;
        Ok(payload.clone())
    }
    fn admin(&mut self, operation: &Value) -> Result {
        Self::signature(operation, &self.admin_public_key, CTX_ADMIN_OP)?;
        require(
            !self
                .admin_operations
                .contains_key(s(operation, "operation_id")),
            "C_SCHEMA_VIOLATION",
        )?;
        require(self.pending_admin.is_none(), "C_MISSING_DEPENDENCY")?;
        require(
            s(operation, "expected_parameters_hash")
                == object_hash(
                    self.parameters
                        .as_ref()
                        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?,
                )
                && s(operation, "expected_epoch_hash")
                    == object_hash(&self.current_epoch()?.record),
            "C_SCHEMA_VIOLATION",
        )?;
        match s(operation, "action") {
            "approve_enrollment" => {
                require(self.staged_envelopes.is_empty(), "C_SCHEMA_VIOLATION")?;
                let devices = self
                    .control_batch
                    .iter()
                    .filter(|v| v.kind == "device")
                    .collect::<Vec<_>>();
                require(devices.len() == 1, "C_MISSING_DEPENDENCY")?;
                let envelopes = self
                    .control_batch
                    .iter()
                    .filter(|v| v.kind == "envelope")
                    .map(|v| v.object.clone())
                    .collect::<Vec<_>>();
                self.target(
                    operation,
                    serde_json::json!({"device_record":devices[0].object,"envelopes":envelopes}),
                )?;
                self.control_batch.clear();
                self.action_payloads.remove(s(operation, "target_hash"));
            }
            "revoke_device" => self.pending_admin = Some(operation.clone()),
            "prune_log" | "delete_chain" => {
                self.complete_cut()?;
                let payload = self
                    .action_payloads
                    .get(s(operation, "target_hash"))
                    .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
                admin_action::verify(
                    operation,
                    payload,
                    self.parameters.as_ref().unwrap(),
                    &self.current_epoch()?.record,
                )?;
                self.action_payloads.remove(s(operation, "target_hash"));
                self.control_batch.clear();
            }
            "reseed" => return Err(ClientError("C_SNAPSHOT_INVALID")),
            _ => return Err(ClientError("C_SCHEMA_VIOLATION")),
        }
        self.admin_operations
            .insert(s(operation, "operation_id").into(), object_hash(operation));
        Ok(())
    }
    fn device(&mut self, record: &Value, seq: u64) -> Result {
        require(
            self.pending_admin.is_none() && self.staged_envelopes.is_empty(),
            "C_MISSING_DEPENDENCY",
        )?;
        if self.current_epoch().is_ok()
            && !self
                .control_batch
                .iter()
                .any(|v| v.kind == "device" && v.object["enrollment"]["signer_kind"] == "admin")
        {
            self.complete_cut()?;
            self.control_batch.clear();
        }
        let id = s(record, "device_id");
        require(!self.devices.contains_key(id), "C_DEVICE_EQUIVOCATION")?;
        let cert = &record["enrollment"];
        require(
            !self
                .devices
                .values()
                .any(|d| d.record["enrollment"]["certificate_id"] == cert["certificate_id"]),
            "C_SCHEMA_VIOLATION",
        )?;
        Self::signature(record, s(record, "device_public_key"), CTX_DEVICE_RECORD)?;
        if cert["signer_kind"] == "device" {
            require(
                self.policy("allow_peer_enrollment") && s(cert, "signer_device_id") != id,
                "C_SCHEMA_VIOLATION",
            )?;
        }
        Self::signature(cert, self.signer_key(cert, seq)?, CTX_ENROLLMENT)?;
        if let Ok(current) = self.current_epoch() {
            require(
                self.epochs.contains_key(&(n(record, "meta_epoch") as u32))
                    || (n(record, "meta_epoch") == n(&current.record, "epoch") + 1
                        && cert["signer_kind"] == "admin"),
                "C_MISSING_DEPENDENCY",
            )?;
        } else {
            require(
                n(record, "meta_epoch") == 0 && cert["signer_kind"] == "admin",
                "C_SCHEMA_VIOLATION",
            )?;
        }
        self.devices.insert(
            id.into(),
            Admission {
                record: record.clone(),
                enrolled_seq: seq,
                revoked_seq: None,
            },
        );
        self.control_batch.push(ControlObject {
            kind: "device".into(),
            object: record.clone(),
        });
        Ok(())
    }
    fn envelope(&mut self, envelope: &Value, seq: u64) -> Result {
        Self::signature(envelope, self.signer_key(envelope, seq)?, CTX_ENVELOPE)?;
        if envelope["recipient_kind"] == "device" {
            let recipient = self.subject(s(envelope, "recipient_device_id"), self.generation()?)?;
            require(recipient.revoked_seq.is_none(), "C_ACCEPTANCE_UNPROVEN")?;
            require(
                s(envelope, "recipient_key_hash")
                    == hash_bytes(
                        &decode(s(&recipient.record, "device_kex_public_key"), Some(32))
                            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
                    ),
                "C_SCHEMA_VIOLATION",
            )?;
        }
        if let Some(epoch) = self.epochs.get(&(n(envelope, "epoch") as u32)) {
            for field in [
                "chain_id",
                "generation",
                "epoch",
                "previous_epoch_hash",
                "epoch_commitment",
            ] {
                require(envelope[field] == epoch.record[field], "C_EPOCH_FORK")?;
            }
            // Recovery replacements are forbidden, and old envelopes never grant new-write authority.
            require(envelope["recipient_kind"] == "device", "C_SCHEMA_VIOLATION")?;
        } else {
            let next = self
                .epochs
                .last_key_value()
                .map_or(0, |(n, _)| u64::from(*n) + 1);
            require(n(envelope, "epoch") == next, "C_MISSING_DEPENDENCY")?;
            if self.staged_envelopes.is_empty()
                && self.current_epoch().is_ok()
                && !self
                    .control_batch
                    .iter()
                    .any(|v| v.kind == "device" && v.object["enrollment"]["signer_kind"] == "admin")
            {
                self.complete_cut()?;
                self.control_batch.clear();
            }
            require(
                !self
                    .staged_envelopes
                    .contains_key(s(envelope, "envelope_id")),
                "C_SCHEMA_VIOLATION",
            )?;
            self.staged_envelopes
                .insert(s(envelope, "envelope_id").into(), envelope.clone());
        }
        if !self.staged_envelopes.is_empty()
            || self.control_batch.iter().any(|v| v.kind == "device")
        {
            self.control_batch.push(ControlObject {
                kind: "envelope".into(),
                object: envelope.clone(),
            });
        }
        Ok(())
    }
    fn epoch(&mut self, epoch: &Value, seq: u64, time: u64) -> Result {
        let next = self
            .epochs
            .last_key_value()
            .map_or(0, |(n, _)| u64::from(*n) + 1);
        require(n(epoch, "epoch") == next, "C_EPOCH_FORK")?;
        let previous = self
            .epochs
            .last_key_value()
            .map(|(_, e)| Value::String(object_hash(&e.record)))
            .unwrap_or(Value::Null);
        require(epoch["previous_epoch_hash"] == previous, "C_EPOCH_FORK")?;
        if epoch["signer_kind"] == "device" {
            require(
                self.policy("allow_device_rotation")
                    && matches!(
                        s(epoch, "reason"),
                        "manual" | "scheduled" | "device_revocation" | "key_rotation"
                    ),
                "C_SCHEMA_VIOLATION",
            )?;
        } else if next == 0 {
            require(epoch["reason"] == "genesis", "C_SCHEMA_VIOLATION")?;
        } else {
            require(
                !matches!(s(epoch, "reason"), "genesis" | "reseed"),
                "C_SCHEMA_VIOLATION",
            )?;
        }
        Self::signature(epoch, self.signer_key(epoch, seq)?, CTX_EPOCH)?;
        let revoked: HashSet<&str> = a(epoch, "revoked_device_ids")
            .iter()
            .map(|v| v.as_str().unwrap())
            .collect();
        if next == 0 {
            require(
                revoked.is_empty()
                    && self.devices.len() == 1
                    && self
                        .devices
                        .values()
                        .all(|d| d.record["enrollment"]["request_hash"].is_null()),
                "C_SCHEMA_VIOLATION",
            )?;
        }
        require(
            revoked.is_empty()
                || matches!(
                    s(epoch, "reason"),
                    "device_revocation" | "key_rotation" | "emergency" | "reseed"
                ),
            "C_SCHEMA_VIOLATION",
        )?;
        if let Some(operation) = &self.pending_admin {
            let envelopes = self
                .control_batch
                .iter()
                .filter(|v| v.kind == "envelope")
                .map(|v| v.object.clone())
                .collect::<Vec<_>>();
            let devices = self
                .control_batch
                .iter()
                .filter(|v| v.kind == "device")
                .map(|v| v.object.clone())
                .collect::<Vec<_>>();
            let mut inferred = serde_json::json!({"epoch":epoch,"envelopes":envelopes});
            if !devices.is_empty() {
                inferred["device_records"] = serde_json::json!(devices);
            }
            self.target(operation, inferred)?;
        } else {
            require(
                epoch["signer_kind"] != "admin" || revoked.is_empty(),
                "C_MISSING_DEPENDENCY",
            )?;
            require(
                next == 0 || !self.control_batch.iter().any(|v| v.kind == "device"),
                "C_MISSING_DEPENDENCY",
            )?;
        }
        for id in &revoked {
            require(
                self.subject(id, self.generation()?)?.revoked_seq.is_none(),
                "C_SCHEMA_VIOLATION",
            )?;
        }
        let members: HashSet<&str> = a(epoch, "members")
            .iter()
            .map(|v| v.as_str().unwrap())
            .collect();
        let expected: HashSet<&str> = self
            .devices
            .iter()
            .filter(|(id, d)| {
                d.revoked_seq.is_none()
                    && n(&d.record, "generation") == n(epoch, "generation")
                    && !revoked.contains(id.as_str())
            })
            .map(|(id, _)| id.as_str())
            .collect();
        require(members == expected, "C_SCHEMA_VIOLATION")?;
        let mut delivered = HashSet::new();
        let mut recovery = 0;
        for envelope in self.staged_envelopes.values() {
            for field in [
                "chain_id",
                "generation",
                "epoch",
                "previous_epoch_hash",
                "epoch_commitment",
            ] {
                require(envelope[field] == epoch[field], "C_EPOCH_FORK")?;
            }
            if envelope["recipient_kind"] == "recovery" {
                recovery += 1;
                require(
                    object_hash(envelope) == s(epoch, "recovery_envelope_hash"),
                    "C_EPOCH_FORK",
                )?;
            } else {
                let id = s(envelope, "recipient_device_id");
                require(
                    members.contains(id) && delivered.insert(id),
                    "C_SCHEMA_VIOLATION",
                )?;
            }
        }
        require(
            recovery == 1 && delivered == members,
            "C_MISSING_DEPENDENCY",
        )?;
        for id in revoked {
            self.devices.get_mut(id).unwrap().revoked_seq = Some(seq);
        }
        self.staged_envelopes.clear();
        self.control_batch.clear();
        if let Some(operation) = self.pending_admin.take() {
            self.action_payloads.remove(s(&operation, "target_hash"));
        }
        self.epochs.insert(
            next as u32,
            AcceptedEpoch {
                record: epoch.clone(),
                relay_seq: seq,
                committed_at_ms: time,
            },
        );
        Ok(())
    }
    /// An unsigned mutation is malformed input, not authenticated device equivocation evidence.
    /// The enclosing verified prefix determines acceptance, not a mutable registry row.
    pub fn current_key_recipient(&self, device: &str) -> Result {
        self.complete_cut()?;
        let admission = self.subject(device, self.generation()?)?;
        require(admission.revoked_seq.is_none(), "C_RECOVERY_AUTH_REQUIRED")?;
        let current = self.current_epoch()?;
        // §10.4/§13.2: a later approval adds a current-key envelope; it cannot
        // rewrite the already signed epoch's original membership array.
        if admission.enrolled_seq <= current.relay_seq {
            require(
                a(&current.record, "members").iter().any(|id| id == device),
                "C_MISSING_DEPENDENCY",
            )?;
        }
        Ok(())
    }

    pub fn device_envelope_eligible(&self, envelope: &Value, sequence: u64) -> Result {
        wire::validate(envelope, "envelope")?;
        require(
            sequence > 0 && sequence <= self.sequence && envelope["recipient_kind"] == "device",
            "C_ACCEPTANCE_UNPROVEN",
        )?;
        let epoch = self
            .epochs
            .get(&(n(envelope, "epoch") as u32))
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
        for field in [
            "chain_id",
            "generation",
            "epoch",
            "previous_epoch_hash",
            "epoch_commitment",
        ] {
            require(envelope[field] == epoch.record[field], "C_EPOCH_FORK")?;
        }
        let recipient = self
            .devices
            .get(s(envelope, "recipient_device_id"))
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
        require(
            recipient.enrolled_seq <= sequence
                && recipient.revoked_seq.is_none_or(|at| sequence < at),
            "C_ACCEPTANCE_UNPROVEN",
        )?;
        require(
            envelope["recipient_key_hash"]
                == hash_bytes(
                    &decode(s(&recipient.record, "device_kex_public_key"), Some(32))
                        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
                ),
            "C_SCHEMA_VIOLATION",
        )?;
        Self::signature(envelope, self.signer_key(envelope, sequence)?, CTX_ENVELOPE)
    }

    pub fn known_device(&self, record: &Value) -> Result<Value> {
        wire::validate(record, "device").map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let known = self
            .devices
            .get(s(record, "device_id"))
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
        Self::signature(
            record,
            s(&known.record, "device_public_key"),
            CTX_DEVICE_RECORD,
        )?;
        require(record == &known.record, "C_DEVICE_EQUIVOCATION")?;
        Ok(serde_json::json!({"device_record":record,"revoked":known.revoked_seq.is_some()}))
    }

    pub fn record_eligible(&self, record: &Value, sequence: u64) -> Result {
        wire::validate(record, "record").map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        require(
            s(record, "chain_id") == self.pin.chain_id
                && n(record, "generation") == u64::from(self.generation()?),
            "C_SCHEMA_VIOLATION",
        )?;
        let epoch = self
            .epochs
            .get(&(n(record, "epoch") as u32))
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
        require(epoch.relay_seq < sequence, "C_ACCEPTANCE_UNPROVEN")?;
        if let Some((_, successor)) = self
            .epochs
            .range((
                std::ops::Bound::Excluded(n(record, "epoch") as u32),
                std::ops::Bound::Unbounded,
            ))
            .next()
        {
            require(sequence < successor.relay_seq, "C_ACCEPTANCE_UNPROVEN")?;
        }
        // A newly approved subject may acknowledge and write within the current epoch even
        // before a subsequent rotation names it in members. Its logged certificate is required.
        let key = self.eligible_key(
            s(record, "device_id"),
            n(record, "generation") as u32,
            sequence,
        )?;
        Self::signature(record, key, CTX_SYNC_RECORD)?;
        let private = self.parameters.as_ref().unwrap()["policy"]["metadata_profile"] == "private";
        require(
            private == record["collection_tag"].is_null(),
            "C_COLLECTION_TAG_MISMATCH",
        )
    }
    /// A signed original receipt binds this exact object and the full H recurrence.
    /// It is retained separately; transparency inclusion is tracked separately (§11.2).
    pub fn advance(&self, entry: &Value, receipt: &Value) -> Result<Self> {
        attestations::validate_log_entry(entry).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        require(
            n(entry, "relay_seq") == self.sequence + 1,
            "C_MISSING_DEPENDENCY",
        )?;
        attestations::verify_receipt(receipt, &entry["object"], Some(entry), &self.pin)?;
        require(
            s(receipt, "previous_head_hash") == self.head_hash,
            "C_LOG_HASH_MISMATCH",
        )?;
        let mut next = self.clone();
        let record = &entry["object"];
        require(
            s(record, "chain_id") == self.pin.chain_id,
            "C_SCHEMA_VIOLATION",
        )?;
        let seq = n(entry, "relay_seq");
        if s(entry, "kind") != "parameters" {
            require(
                n(record, "generation") == u64::from(self.generation()?),
                "C_MISSING_DEPENDENCY",
            )?;
        }
        match s(entry, "kind") {
            "parameters" => {
                require(
                    s(record, "admin_public_key") == self.admin_public_key,
                    "C_SIGNATURE_INVALID",
                )?;
                Self::signature(record, &self.admin_public_key, CTX_PARAMETERS)?;
                if let Some(old) = &self.parameters {
                    require(
                        n(record, "generation") == n(old, "generation") + 1
                            && s(record, "previous_parameters_hash") == object_hash(old),
                        "C_EPOCH_FORK",
                    )?;
                    // A generation switch is installed with verified snapshot state, never an
                    // isolated parameters row. The snapshot installer supplies that transition.
                    return Err(ClientError("C_SNAPSHOT_INVALID"));
                }
                require(
                    n(record, "generation") == 0 && seq == 1,
                    "C_BOOTSTRAP_TRUST_REQUIRED",
                )?;
                next.parameters = Some(record.clone());
            }
            "device" => next.device(record, seq)?,
            "envelope" => next.envelope(record, seq)?,
            "epoch" => next.epoch(record, seq, n(entry, "committed_at_ms"))?,
            "record" => {
                next.complete_cut()?;
                next.record_eligible(record, seq)?;
                next.control_batch.clear();
            }
            "admin" => next.admin(record)?,
            "compaction" => {
                next.complete_cut()?;
                require(
                    s(record, "log_id") == self.pin.log_id
                        && n(record, "covered_through_seq") <= self.sequence,
                    "C_SNAPSHOT_INVALID",
                )?;
                Self::signature(
                    record,
                    next.eligible_key(s(record, "device_id"), self.generation()?, seq)?,
                    CTX_COMPACTION,
                )?;
                next.control_batch.clear();
            }
            _ => return Err(ClientError("C_SCHEMA_VIOLATION")),
        }
        next.sequence = seq;
        next.head_hash = attestations::data_link(&self.head_hash, entry)?;
        Ok(next)
    }
}
