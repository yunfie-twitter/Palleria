//! Foreground epoch proposals (§7.6/§7.7/§7.10/§13.3/§13.4).
//! Root participation produces and opens the recovery envelope locally. The relay
//! receives only the complete signed public/ciphertext bundle, never this material.
use crate::{
    authority::Authority,
    client_error::{ClientError, Result},
    crypto,
    locked_memory::Locked,
    models::EpochRecord,
    public_wire::{self as wire, n, s},
    recovery,
    root_session::{self, Operation},
    strict::verify::{CTX_ADMIN_OP, CTX_EPOCH, object_hash},
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::SigningKey;
use serde::Deserialize;
use serde_json::{Value, json};
use std::collections::BTreeSet;
use subtle::ConstantTimeEq;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

/// Internal operation input, not an added REST/wire object.
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Request {
    pub reason: String,
    pub signer_kind: String,
    #[serde(deserialize_with = "crate::models::required_nullable")]
    pub signer_device_id: Option<String>,
    pub revoked_device_ids: Vec<String>,
    pub created_at_ms: u64,
}
#[derive(Zeroize, ZeroizeOnDrop)]
pub(crate) struct Material {
    pub record: [u8; 32],
    pub metadata: [u8; 32],
    pub tag: [u8; 32],
    pub secret: [u8; 32],
}
pub struct PreparedEpoch {
    pub public: Value,
    pub(crate) material: Locked<Material>,
}
impl PreparedEpoch {
    pub fn secret(&self) -> &[u8; 32] {
        &self.material.secret
    }
}
fn require(ok: bool, code: &'static str) -> Result {
    if ok { Ok(()) } else { Err(ClientError(code)) }
}

pub fn prepare(
    handle: u64,
    operation: Operation,
    authority: &Authority,
    request: Request,
    device_seed: &[u8],
) -> Result<PreparedEpoch> {
    require(
        matches!(operation, Operation::Rotate | Operation::Revoke),
        "C_RECOVERY_AUTH_REQUIRED",
    )?;
    require(
        wire::check_type(&json!(request.created_at_ms), "int")
            && matches!(request.signer_kind.as_str(), "admin" | "device"),
        "C_SCHEMA_VIOLATION",
    )?;
    require(
        request.revoked_device_ids.windows(2).all(|w| w[0] < w[1])
            && request.revoked_device_ids.iter().all(|v| wire::uuid(v)),
        "C_SCHEMA_VIOLATION",
    )?;
    require(
        matches!(
            request.reason.as_str(),
            "manual" | "scheduled" | "key_rotation" | "device_revocation" | "emergency"
        ),
        "C_SCHEMA_VIOLATION",
    )?;
    require(
        request.revoked_device_ids.is_empty()
            || matches!(
                request.reason.as_str(),
                "device_revocation" | "key_rotation" | "emergency"
            ),
        "C_SCHEMA_VIOLATION",
    )?;
    require(
        !matches!(
            request.reason.as_str(),
            "device_revocation" | "key_rotation"
        ) || !request.revoked_device_ids.is_empty(),
        "C_SCHEMA_VIOLATION",
    )?;
    require(
        operation != Operation::Revoke || !request.revoked_device_ids.is_empty(),
        "C_RECOVERY_AUTH_REQUIRED",
    )?;
    authority.complete_cut()?;
    let generation = authority.generation()?;
    let previous = authority.current_epoch()?;
    let epoch = (n(&previous.record, "epoch") as u32)
        .checked_add(1)
        .ok_or(ClientError("C_COUNTER_EXHAUSTED"))?;
    let previous_hash = object_hash(&previous.record);
    let parameters = authority
        .parameters
        .as_ref()
        .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
    if request.reason == "scheduled" {
        let interval = n(&parameters["policy"], "scheduled_rotation_ms");
        require(
            interval > 0
                && request.created_at_ms >= previous.committed_at_ms
                && request.created_at_ms - previous.committed_at_ms >= interval,
            "C_MISSING_DEPENDENCY",
        )?;
    }
    let revoked = request
        .revoked_device_ids
        .iter()
        .map(String::as_str)
        .collect::<BTreeSet<_>>();
    let approved = authority
        .devices
        .iter()
        .filter(|(_, d)| {
            d.revoked_seq.is_none() && n(&d.record, "generation") == u64::from(generation)
        })
        .collect::<Vec<_>>();
    require(
        revoked
            .iter()
            .all(|id| approved.iter().any(|(known, _)| known.as_str() == *id)),
        "C_SCHEMA_VIOLATION",
    )?;
    let members = approved
        .iter()
        .filter(|(id, _)| !revoked.contains(id.as_str()))
        .map(|(id, _)| (*id).clone())
        .collect::<Vec<_>>();
    // Revoking every member requires the separately staged fresh subject in §13.4;
    // this proposal path never fabricates or silently enrolls that identity.
    require(!members.is_empty(), "C_MISSING_DEPENDENCY")?;
    root_session::with(handle, operation, |root| {
        require(
            root.roots.chain_id == authority.pin.chain_id
                && root.roots.admin_public_key == authority.admin_public_key,
            "C_RECOVERY_ROOT_MISMATCH",
        )?;
        let device = if request.signer_kind == "device" {
            require(
                parameters["policy"]["allow_device_rotation"] == true
                    && request.reason != "emergency",
                "C_RECOVERY_AUTH_REQUIRED",
            )?;
            let id = request
                .signer_device_id
                .as_deref()
                .ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
            let subject = authority
                .devices
                .get(id)
                .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
            require(
                subject.revoked_seq.is_none()
                    && n(&subject.record, "generation") == u64::from(generation),
                "C_RECOVERY_AUTH_REQUIRED",
            )?;
            let seed = Zeroizing::new(
                <[u8; 32]>::try_from(device_seed).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            );
            let signing = SigningKey::from_bytes(&seed);
            require(
                URL_SAFE_NO_PAD.encode(signing.verifying_key().as_bytes())
                    == s(&subject.record, "device_public_key"),
                "C_BOOTSTRAP_TRUST_REQUIRED",
            )?;
            Some(signing)
        } else {
            require(
                device_seed.is_empty()
                    && request.signer_device_id.is_none()
                    && matches!(request.reason.as_str(), "emergency" | "device_revocation"),
                "C_RECOVERY_AUTH_REQUIRED",
            )?;
            None
        };
        let signing = device.as_ref().unwrap_or(&root.roots.admin_key);
        let secret = Zeroizing::new(crypto::generate_epoch_secret());
        let keys = crypto::derive_epoch_keys(&root.roots.chain_id_digest, epoch, &secret)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let recovery_env = crypto::wrap_recovery_epoch_envelope(
            &uuid::Uuid::new_v4().to_string(),
            &root.roots.chain_id,
            generation,
            epoch,
            Some(&previous_hash),
            &keys.epoch_commitment_b64u,
            &request.signer_kind,
            request.signer_device_id.as_deref(),
            signing,
            &root.roots.recovery_kek,
            &secret,
            None,
        )
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let opened = Zeroizing::new(
            crypto::unwrap_recovery_epoch_envelope(
                &recovery_env,
                &root.roots.recovery_kek,
                &root.roots.chain_id_digest,
            )
            .map_err(|_| ClientError("C_COMMITMENT_MISMATCH"))?,
        );
        require(
            bool::from(secret.as_slice().ct_eq(opened.as_slice())),
            "C_COMMITMENT_MISMATCH",
        )?;
        let recovery_env =
            serde_json::to_value(recovery_env).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let mut envelopes = Vec::new();
        for id in &members {
            let recipient = &authority.devices[id].record;
            let envelope = crypto::wrap_device_epoch_envelope(
                &uuid::Uuid::new_v4().to_string(),
                &root.roots.chain_id,
                generation,
                epoch,
                Some(&previous_hash),
                &keys.epoch_commitment_b64u,
                id,
                s(recipient, "device_kex_public_key"),
                &request.signer_kind,
                request.signer_device_id.as_deref(),
                signing,
                &secret,
            )
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
            envelopes.push(
                serde_json::to_value(envelope).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            );
        }
        envelopes.push(recovery_env.clone());
        let epoch_record = EpochRecord {
            chain_id: root.roots.chain_id.clone(),
            generation,
            epoch,
            previous_epoch_hash: Some(previous_hash.clone()),
            epoch_commitment: keys.epoch_commitment_b64u.clone(),
            reason: request.reason.clone(),
            signer_kind: request.signer_kind.clone(),
            signer_device_id: request.signer_device_id.clone(),
            members,
            revoked_device_ids: request.revoked_device_ids.clone(),
            recovery_envelope_hash: object_hash(&recovery_env),
            ext: None,
            signature: String::new(),
        };
        let mut epoch_record =
            serde_json::to_value(epoch_record).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        recovery::sign(&mut epoch_record, signing, CTX_EPOCH)?;
        wire::validate(&epoch_record, "epoch").map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let action = json!({"epoch":epoch_record,"envelopes":envelopes});
        let admin = if request.signer_kind == "admin" && !revoked.is_empty() {
            let mut value = json!({"operation_id":uuid::Uuid::new_v4().to_string(),"chain_id":root.roots.chain_id,"generation":generation,"action":"revoke_device","expected_parameters_hash":object_hash(parameters),"expected_epoch_hash":previous_hash,"target_hash":object_hash(&action),"created_at_ms":request.created_at_ms});
            recovery::sign(&mut value, &root.roots.admin_key, CTX_ADMIN_OP)?;
            crate::admin_action::verify(&value, &action, parameters, &previous.record)?;
            value
        } else {
            Value::Null
        };
        let public = json!({"chain_id":root.roots.chain_id,"generation":generation,"epoch":epoch,"epoch_hash":object_hash(&epoch_record),"epoch_commitment":keys.epoch_commitment_b64u,"request_body":{"expected_epoch_hash":previous_hash,"epoch":epoch_record,"envelopes":envelopes,"admin_operation":admin}});
        let material = Locked::new(Material {
            record: keys.record_key,
            metadata: keys.device_meta_key,
            tag: keys.collection_tag_key,
            secret: *secret,
        })?;
        Ok(PreparedEpoch { public, material })
    })
}
