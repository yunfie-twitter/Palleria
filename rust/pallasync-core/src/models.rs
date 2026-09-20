use serde::{Deserialize, Serialize};

pub const PROTOCOL_VERSION_2_1: &str = "2.1";
pub const PROTOCOL_VERSION_2_0: &str = "2.0";

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct SyncRecord {
    pub protocol_version: String,
    pub chain_id: String,
    pub record_id: String,
    #[serde(default)]
    pub epoch: i64,
    pub collection_name: String,
    pub action: String,            // "upsert" or "delete"
    pub encrypted_payload: String, // Base64url encoded XChaCha20Poly1305 ciphertext
    #[serde(default)]
    pub payload_nonce: String, // Base64url encoded 24-byte random nonce
    pub device_id: String,
    #[serde(default)]
    pub lamport: i64,
    pub created_at_ms: i64,
    #[serde(default)]
    pub signature: String, // Base64url encoded ed25519 signature
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct RecordAAD<'a> {
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
pub struct DeviceRecord {
    pub protocol_version: String,
    pub chain_id: String,
    pub device_id: String,
    pub device_public_key: String, // Base64url encoded
    pub encrypted_device_name: String,
    #[serde(default)]
    pub device_name_nonce: String, // Base64url encoded 24-byte nonce
    #[serde(default = "default_status_active")]
    pub status: String,
    pub created_at_ms: i64,
    #[serde(default)]
    pub updated_at_ms: i64,
    #[serde(default)]
    pub signature: String,
}

fn default_status_active() -> String {
    "active".to_string()
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct CapabilityToken {
    pub v: i32,
    pub chain_id: String,
    pub device_id: String,
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

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct InvitationBundle {
    pub protocol_version: String,
    pub chain_id: String,
    pub chain_salt: String,
    pub server_url: String,
    pub invitation_id: String,
    pub issued_at_ms: i64,
    pub expires_at_ms: i64,
    pub one_time: bool,
    pub inviter_device_id: String,
    pub inviter_public_key: String,
    #[serde(default)]
    pub inviter_signature: String,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct ChainParameters {
    pub protocol_version: String,
    pub chain_id: String,
    pub chain_salt: String,
    pub created_at_ms: i64,
    pub creator_device_id: String,
    pub creator_public_key: String,
    pub admin_public_key: String,
    #[serde(default)]
    pub signature: String,
}

pub fn to_jcs<T: Serialize>(value: &T) -> Result<Vec<u8>, serde_json::Error> {
    serde_jcs::to_vec(value)
}

pub fn unsigned_record_jcs<T: Serialize>(record: &T) -> Result<Vec<u8>, serde_json::Error> {
    let mut value = serde_json::to_value(record)?;
    if let Some(object) = value.as_object_mut() {
        object.remove("signature");
        object.remove("inviter_signature");
        object.remove("enrollment_proof");
        object.remove("admin_proof");
        object.remove("relay_seq");
    }
    to_jcs(&value)
}

pub fn sync_record_signing_bytes(record: &SyncRecord) -> Result<Vec<u8>, serde_json::Error> {
    unsigned_record_jcs(record)
}

pub fn device_record_signing_bytes(record: &DeviceRecord) -> Result<Vec<u8>, serde_json::Error> {
    unsigned_record_jcs(record)
}

pub fn capability_token_signing_bytes(
    token: &CapabilityToken,
) -> Result<Vec<u8>, serde_json::Error> {
    unsigned_record_jcs(token)
}

pub fn invitation_bundle_signing_bytes(
    bundle: &InvitationBundle,
) -> Result<Vec<u8>, serde_json::Error> {
    unsigned_record_jcs(bundle)
}
