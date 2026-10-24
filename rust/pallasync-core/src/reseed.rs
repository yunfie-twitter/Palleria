//! Root-owned material for a fresh §17.5 generation identity.
//! Snapshot construction and the final AdminOperation remain separate commit steps.
use crate::{
    authority::Authority,
    client_error::{ClientError, Result},
    crypto,
    locked_memory::Locked,
    models::{
        self, ChainParameters, DeviceMetaPlaintext, DeviceRecord, EnrollmentCertificate,
        EpochKeyEnvelope, EpochRecord,
    },
    root_session::{self, Operation},
    strict::verify::{CTX_ENROLLMENT, CTX_EPOCH, object_hash},
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use rand::RngCore;
use serde::Serialize;
use serde_json::Value;
use zeroize::{Zeroize, ZeroizeOnDrop, Zeroizing};

#[derive(Zeroize, ZeroizeOnDrop)]
pub(crate) struct Material {
    pub record: [u8; 32],
    pub metadata: [u8; 32],
    pub tag: [u8; 32],
    pub epoch_secret: [u8; 32],
    pub device_signing_seed: [u8; 32],
    pub device_kex_private: [u8; 32],
}

pub struct Prepared {
    pub(crate) public: Value,
    pub(crate) material: Locked<Material>,
}

impl Prepared {
    pub fn public(&self) -> &Value {
        &self.public
    }
}

#[derive(Serialize)]
struct PublicResult {
    chain_id: String,
    generation: u32,
    device_id: String,
    parameters_hash: String,
    epoch_hash: String,
    epoch_commitment: String,
    parameters: ChainParameters,
    #[serde(rename = "epoch")]
    epoch_record: EpochRecord,
    device_record: DeviceRecord,
    envelopes: Vec<EpochKeyEnvelope>,
}

/// Create one fresh local member. Old non-revoked members are explicitly retired by the
/// new epoch; no old device key is reused or granted membership in the new generation.
pub fn prepare(
    handle: u64,
    authority: &Authority,
    device_name: &str,
    created_at_ms: i64,
) -> Result<Prepared> {
    if created_at_ms < 0 || !(1..=256).contains(&device_name.len()) {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    }
    authority.complete_cut()?;
    root_session::with(handle, Operation::Reseed, |session| {
        let current_parameters = authority
            .parameters
            .as_ref()
            .ok_or(ClientError("C_MISSING_DEPENDENCY"))?;
        let current_epoch = authority.current_epoch()?;
        let roots = &session.roots;
        if roots.chain_id != authority.pin.chain_id
            || roots.admin_public_key != authority.admin_public_key
            || current_parameters["chain_id"] != roots.chain_id
            || current_parameters["admin_public_key"] != roots.admin_public_key
        {
            return Err(ClientError("C_RECOVERY_ROOT_MISMATCH"));
        }
        let generation = authority
            .generation()?
            .checked_add(1)
            .ok_or(ClientError("C_COUNTER_EXHAUSTED"))?;
        let epoch = u32::try_from(
            current_epoch.record["epoch"]
                .as_u64()
                .ok_or(ClientError("C_SCHEMA_VIOLATION"))?
                .checked_add(1)
                .ok_or(ClientError("C_COUNTER_EXHAUSTED"))?,
        )
        .map_err(|_| ClientError("C_COUNTER_EXHAUSTED"))?;
        let device_id = uuid::Uuid::new_v4().to_string();
        let certificate_id = uuid::Uuid::new_v4().to_string();
        let device_envelope_id = uuid::Uuid::new_v4().to_string();
        let recovery_envelope_id = uuid::Uuid::new_v4().to_string();

        let mut parameters: ChainParameters = serde_json::from_value(current_parameters.clone())
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        parameters.generation = generation;
        parameters.previous_parameters_hash = Some(object_hash(current_parameters));
        parameters.signature.clear();
        let canonical = models::unsigned_record_jcs(&parameters)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        parameters.signature =
            crypto::sign_with_context(&roots.admin_key, crypto::CTX_CHAIN_PARAMS, &canonical);

        let (device_signing, device_kex) = crypto::generate_device_keys();
        let device_kex = Zeroizing::new(device_kex);
        let device_kex_secret = x25519_dalek::StaticSecret::from(*device_kex);
        let device_kex_public = x25519_dalek::PublicKey::from(&device_kex_secret);
        let device_kex_public_b64 = URL_SAFE_NO_PAD.encode(device_kex_public.as_bytes());
        let device_public_b64 = URL_SAFE_NO_PAD.encode(device_signing.verifying_key().as_bytes());
        let mut certificate = EnrollmentCertificate {
            chain_id: roots.chain_id.clone(),
            generation,
            certificate_id,
            device_id: device_id.clone(),
            device_public_key: device_public_b64.clone(),
            device_kex_public_key: device_kex_public_b64.clone(),
            signer_kind: "admin".into(),
            signer_device_id: None,
            request_hash: None,
            approved_at_ms: created_at_ms,
            ext: None,
            signature: String::new(),
        };
        let canonical = models::unsigned_record_jcs(&certificate)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        certificate.signature =
            crypto::sign_with_context(&roots.admin_key, CTX_ENROLLMENT, &canonical);

        let mut epoch_secret = Zeroizing::new([0u8; 32]);
        rand::thread_rng().fill_bytes(&mut *epoch_secret);
        let epoch_keys = crypto::derive_epoch_keys(&roots.chain_id_digest, epoch, &epoch_secret)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let recovery_envelope = crypto::wrap_recovery_epoch_envelope(
            &recovery_envelope_id,
            &roots.chain_id,
            generation,
            epoch,
            Some(&object_hash(&current_epoch.record)),
            &epoch_keys.epoch_commitment_b64u,
            "admin",
            None,
            &roots.admin_key,
            &roots.recovery_kek,
            &epoch_secret,
            None,
        )
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let device_envelope = crypto::wrap_device_epoch_envelope(
            &device_envelope_id,
            &roots.chain_id,
            generation,
            epoch,
            Some(&object_hash(&current_epoch.record)),
            &epoch_keys.epoch_commitment_b64u,
            &device_id,
            &device_kex_public_b64,
            "admin",
            None,
            &roots.admin_key,
            &epoch_secret,
        )
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let recovery_hash = object_hash(
            &serde_json::to_value(&recovery_envelope)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        let device_record = crypto::create_device_record(
            &roots.chain_id,
            generation,
            &device_id,
            &device_signing,
            &device_kex_public_b64,
            &DeviceMetaPlaintext {
                name: device_name.to_owned(),
                key_protection: "os-keystore".into(),
                ext: None,
            },
            epoch,
            &epoch_keys.device_meta_key,
            certificate,
        )
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let revoked_device_ids = authority
            .devices
            .iter()
            .filter(|(_, admission)| admission.revoked_seq.is_none())
            .map(|(id, _)| id.clone())
            .collect::<Vec<_>>();
        let mut epoch_record = EpochRecord {
            chain_id: roots.chain_id.clone(),
            generation,
            epoch,
            previous_epoch_hash: Some(object_hash(&current_epoch.record)),
            epoch_commitment: epoch_keys.epoch_commitment_b64u.clone(),
            reason: "reseed".into(),
            signer_kind: "admin".into(),
            signer_device_id: None,
            members: vec![device_id.clone()],
            revoked_device_ids,
            recovery_envelope_hash: recovery_hash,
            ext: None,
            signature: String::new(),
        };
        let canonical = models::unsigned_record_jcs(&epoch_record)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        epoch_record.signature = crypto::sign_with_context(&roots.admin_key, CTX_EPOCH, &canonical);

        let public = PublicResult {
            chain_id: roots.chain_id.clone(),
            generation,
            device_id,
            parameters_hash: object_hash(
                &serde_json::to_value(&parameters)
                    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            ),
            epoch_hash: object_hash(
                &serde_json::to_value(&epoch_record)
                    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            ),
            epoch_commitment: epoch_keys.epoch_commitment_b64u.clone(),
            parameters,
            epoch_record,
            device_record,
            envelopes: vec![device_envelope, recovery_envelope],
        };
        let parameters_value = serde_json::to_value(&public.parameters)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let epoch_value = serde_json::to_value(&public.epoch_record)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let device_value = serde_json::to_value(&public.device_record)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        crate::public_wire::validate(&parameters_value, "parameters")?;
        crate::public_wire::validate(&epoch_value, "epoch")?;
        crate::public_wire::validate(&device_value, "device")?;
        crate::public_wire::verify(
            &parameters_value,
            &roots.admin_public_key,
            crypto::CTX_CHAIN_PARAMS,
        )?;
        crate::public_wire::verify(
            &device_value["enrollment"],
            &roots.admin_public_key,
            CTX_ENROLLMENT,
        )?;
        crate::public_wire::verify(
            &device_value,
            device_value["device_public_key"]
                .as_str()
                .ok_or(ClientError("C_SCHEMA_VIOLATION"))?,
            crypto::CTX_DEVICE_RECORD,
        )?;
        crate::public_wire::verify(&epoch_value, &roots.admin_public_key, CTX_EPOCH)?;
        for envelope in &public.envelopes {
            let envelope =
                serde_json::to_value(envelope).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
            crate::public_wire::validate(&envelope, "envelope")?;
            crate::public_wire::verify(
                &envelope,
                &roots.admin_public_key,
                crypto::CTX_EPOCH_ENVELOPE,
            )?;
        }
        let material = Material {
            record: epoch_keys.record_key,
            metadata: epoch_keys.device_meta_key,
            tag: epoch_keys.collection_tag_key,
            epoch_secret: *epoch_secret,
            device_signing_seed: device_signing.to_bytes(),
            device_kex_private: *device_kex,
        };
        Ok(Prepared {
            public: serde_json::to_value(public).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            material: Locked::new(material)?,
        })
    })
}
