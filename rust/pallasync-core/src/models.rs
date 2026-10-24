use serde::{Deserialize, Serialize};

pub const PROTOCOL_VERSION_3_0: &str = "3.0";
pub const PROTOCOL_IDENTIFIER: &str = "pallasync/3";
pub const API_BASE_PATH: &str = "/pallasync/v3/";
pub const MEDIA_TYPE: &str = "application/vnd.palleria.sync.v3+json";

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct SyncRecord {
    pub version: String, // "3.0"
    pub chain_id: String,
    pub generation: u32,
    pub record_id: String, // uuid4
    pub device_id: String, // uuid4
    pub epoch: u32,
    #[serde(deserialize_with = "required_nullable")]
    pub collection_tag: Option<String>, // b64u(16) or null in private
    pub payload_nonce: String,     // b64u(24)
    pub encrypted_payload: String, // b64u
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ext: Option<serde_json::Value>,
    #[serde(default)]
    pub signature: String, // b64u(64)
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct RecordAAD<'a> {
    pub r#type: &'static str,  // "PALLASYNC-AAD-v3"
    pub version: &'static str, // "3.0"
    pub chain_id: &'a str,
    pub generation: u32,
    pub record_id: &'a str,
    pub device_id: &'a str,
    pub epoch: u32,
    pub collection_tag: Option<&'a str>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct InnerRecord {
    pub collection: String,
    pub device_seq: u64,
    #[serde(deserialize_with = "required_nullable")]
    pub prev_record_hash: Option<String>,
    pub operations: Vec<Operation>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ext: Option<serde_json::Value>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct Operation {
    pub entity_id: String,
    pub operation: String, // "upsert", "delete", "clear"
    pub lamport: u64,
    pub created_at_ms: i64,
    pub context: serde_json::Value,
    pub body: serde_json::Value,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ext: Option<serde_json::Value>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct DeviceRecord {
    pub version: String, // "3.0"
    pub chain_id: String,
    pub generation: u32,
    pub device_id: String,             // uuid4
    pub device_public_key: String,     // b64u(32) Ed25519
    pub device_kex_public_key: String, // b64u(32) X25519
    pub meta_epoch: u32,
    pub meta_nonce: String, // b64u(24)
    pub encrypted_device_meta: String,
    pub enrollment: EnrollmentCertificate,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ext: Option<serde_json::Value>,
    #[serde(default)]
    pub signature: String, // b64u(64)
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct DeviceMetaPlaintext {
    pub name: String,
    pub key_protection: String, // "os-keystore", "hardware", etc.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ext: Option<serde_json::Value>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct DeviceMetaAAD<'a> {
    pub r#type: &'static str, // "PALLASYNC-DEVICE-META-v3"
    pub chain_id: &'a str,
    pub generation: u32,
    pub device_id: &'a str,
    pub epoch: u32,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct EnrollmentCertificate {
    pub chain_id: String,
    pub generation: u32,
    pub certificate_id: String,
    pub device_id: String,
    pub device_public_key: String,
    pub device_kex_public_key: String,
    pub signer_kind: String, // "admin" or "device"
    #[serde(deserialize_with = "required_nullable")]
    pub signer_device_id: Option<String>,
    #[serde(deserialize_with = "required_nullable")]
    pub request_hash: Option<String>,
    pub approved_at_ms: i64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ext: Option<serde_json::Value>,
    #[serde(default)]
    pub signature: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct ChainParameters {
    pub version: String, // "3.0"
    pub chain_id: String,
    pub admin_public_key: String,
    pub generation: u32,
    #[serde(deserialize_with = "required_nullable")]
    pub previous_parameters_hash: Option<String>,
    pub policy: ChainPolicy,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ext: Option<serde_json::Value>,
    #[serde(default)]
    pub signature: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct ChainPolicy {
    pub metadata_profile: String, // "private" or "queryable"
    pub allow_device_chain_delete: bool,
    pub allow_peer_enrollment: bool,
    pub allow_device_rotation: bool,
    pub epoch_retention: String,    // "retain", "windowed", "immediate"
    pub epoch_window_ms: u64,       // e.g. 2592000000
    pub scheduled_rotation_ms: u64, // e.g. 0
}

impl Default for ChainPolicy {
    fn default() -> Self {
        Self {
            metadata_profile: "private".to_string(),
            allow_device_chain_delete: false,
            allow_peer_enrollment: true,
            allow_device_rotation: true,
            epoch_retention: "retain".to_string(),
            epoch_window_ms: 2592000000,
            scheduled_rotation_ms: 0,
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct EpochRecord {
    pub chain_id: String,
    pub generation: u32,
    pub epoch: u32,
    #[serde(deserialize_with = "required_nullable")]
    pub previous_epoch_hash: Option<String>,
    pub epoch_commitment: String,
    pub reason: String,
    pub signer_kind: String, // "admin" or "device"
    #[serde(deserialize_with = "required_nullable")]
    pub signer_device_id: Option<String>,
    pub members: Vec<String>,
    pub revoked_device_ids: Vec<String>,
    pub recovery_envelope_hash: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ext: Option<serde_json::Value>,
    #[serde(default)]
    pub signature: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct EpochKeyEnvelope {
    pub envelope_id: String,
    pub chain_id: String,
    pub generation: u32,
    pub epoch: u32,
    #[serde(deserialize_with = "required_nullable")]
    pub previous_epoch_hash: Option<String>,
    pub epoch_commitment: String,
    pub recipient_kind: String, // "device" or "recovery"
    #[serde(deserialize_with = "required_nullable")]
    pub recipient_device_id: Option<String>,
    #[serde(deserialize_with = "required_nullable")]
    pub recipient_key_hash: Option<String>,
    pub signer_kind: String, // "admin" or "device"
    #[serde(deserialize_with = "required_nullable")]
    pub signer_device_id: Option<String>,
    #[serde(deserialize_with = "required_nullable")]
    pub enc: Option<String>,
    #[serde(deserialize_with = "required_nullable")]
    pub nonce: Option<String>,
    pub ciphertext: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub ext: Option<serde_json::Value>,
    #[serde(default)]
    pub signature: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct EnvelopeBinding<'a> {
    pub envelope_id: &'a str,
    pub chain_id: &'a str,
    pub generation: u32,
    pub epoch: u32,
    pub previous_epoch_hash: Option<&'a str>,
    pub epoch_commitment: &'a str,
    pub recipient_kind: &'a str,
    pub recipient_device_id: Option<&'a str>,
    pub recipient_key_hash: Option<&'a str>,
    pub signer_kind: &'a str,
    pub signer_device_id: Option<&'a str>,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
#[serde(deny_unknown_fields)]
pub struct CapabilityToken {
    pub v: u32, // 3
    pub aud: String,
    pub chain_id: String,
    pub signer_kind: String, // "device" or "admin"
    #[serde(deserialize_with = "required_nullable")]
    pub device_id: Option<String>,
    pub method: String,
    pub path: String,
    #[serde(default)]
    pub query: String,
    pub body_sha256: String,
    pub issued_at_ms: i64,
    pub expires_at_ms: i64,
    pub nonce: String,
    #[serde(default)]
    pub signature: String,
}

pub fn to_jcs<T: Serialize>(value: &T) -> Result<Vec<u8>, serde_json::Error> {
    serde_jcs::to_vec(value)
}

pub fn parse<T: serde::de::DeserializeOwned>(json: &str) -> Result<T, String> {
    let value = crate::strict::jcs::parse(json.as_bytes()).map_err(str::to_owned)?;
    serde_json::from_value(value).map_err(|_| "C_SCHEMA_VIOLATION".to_owned())
}

pub fn unsigned_record_jcs<T: Serialize>(record: &T) -> Result<Vec<u8>, serde_json::Error> {
    let mut value = serde_json::to_value(record)?;
    if let Some(object) = value.as_object_mut() {
        object.remove("signature");
    }
    to_jcs(&value)
}

/// §3.1 J-7: nullable does not mean optional.
pub fn required_nullable<'de, D, T>(deserializer: D) -> Result<Option<T>, D::Error>
where
    D: serde::Deserializer<'de>,
    T: Deserialize<'de>,
{
    Option::<T>::deserialize(deserializer)
}

// ---------------------------------------------------------------------
// Legacy v2.1 models (retained for migration reading per Protocol 3.0 §9)
// ---------------------------------------------------------------------
pub mod legacy {
    use super::*;

    pub const PROTOCOL_VERSION_2_1: &str = "2.1";
    pub const PROTOCOL_VERSION_2_0: &str = "2.0";

    #[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
    pub struct SyncRecordV20 {
        pub protocol_version: String,
        pub chain_id: String,
        pub record_id: String,
        pub collection_name: String,
        pub action: String,
        pub encrypted_payload: String,
        pub device_id: String,
        pub created_at_ms: i64,
        pub signature: String,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
    pub struct SyncRecordV21 {
        pub protocol_version: String,
        pub chain_id: String,
        pub record_id: String,
        #[serde(default)]
        pub epoch: i64,
        pub collection_name: String,
        pub action: String,
        pub encrypted_payload: String,
        #[serde(default)]
        pub payload_nonce: String,
        pub device_id: String,
        #[serde(default)]
        pub lamport: i64,
        pub created_at_ms: i64,
        #[serde(default)]
        pub signature: String,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
    pub struct RecordAADV21<'a> {
        pub protocol_version: &'a str,
        pub chain_id: &'a str,
        pub record_id: &'a str,
        pub epoch: i64,
        pub collection_name: &'a str,
        pub action: &'a str,
        pub device_id: &'a str,
        pub lamport: i64,
        pub created_at_ms: i64,
    }

    #[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
    pub struct DeviceRecordV21 {
        pub protocol_version: String,
        pub chain_id: String,
        pub device_id: String,
        pub device_public_key: String,
        pub encrypted_device_name: String,
        #[serde(default)]
        pub device_name_nonce: String,
        pub status: String,
        pub created_at_ms: i64,
        #[serde(default)]
        pub updated_at_ms: i64,
        #[serde(default)]
        pub signature: String,
    }
}
