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

#[derive(Serialize)]
struct SyncRecordSigningView<'a> {
    pub protocol_version: &'a str,
    pub chain_id: &'a str,
    pub record_id: &'a str,
    pub epoch: i64,
    pub collection_name: &'a str,
    pub action: &'a str,
    pub encrypted_payload: &'a str,
    pub payload_nonce: &'a str,
    pub device_id: &'a str,
    pub lamport: i64,
    pub created_at_ms: i64,
}

#[derive(Serialize)]
struct DeviceRecordSigningView<'a> {
    pub protocol_version: &'a str,
    pub chain_id: &'a str,
    pub device_id: &'a str,
    pub device_public_key: &'a str,
    pub encrypted_device_name: &'a str,
    pub device_name_nonce: &'a str,
    pub status: &'a str,
    pub created_at_ms: i64,
    pub updated_at_ms: i64,
}

#[derive(Serialize)]
struct CapabilityTokenSigningView<'a> {
    pub v: i32,
    pub chain_id: &'a str,
    pub device_id: &'a str,
    pub method: &'a str,
    pub path: &'a str,
    pub query: &'a str,
    pub body_sha256: &'a str,
    pub issued_at_ms: i64,
    pub expires_at_ms: i64,
    pub nonce: &'a str,
}

#[derive(Serialize)]
struct InvitationBundleSigningView<'a> {
    pub protocol_version: &'a str,
    pub chain_id: &'a str,
    pub chain_salt: &'a str,
    pub server_url: &'a str,
    pub invitation_id: &'a str,
    pub issued_at_ms: i64,
    pub expires_at_ms: i64,
    pub one_time: bool,
    pub inviter_device_id: &'a str,
    pub inviter_public_key: &'a str,
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
    serde_jcs::to_vec(&SyncRecordSigningView {
        protocol_version: &record.protocol_version,
        chain_id: &record.chain_id,
        record_id: &record.record_id,
        epoch: record.epoch,
        collection_name: &record.collection_name,
        action: &record.action,
        encrypted_payload: &record.encrypted_payload,
        payload_nonce: &record.payload_nonce,
        device_id: &record.device_id,
        lamport: record.lamport,
        created_at_ms: record.created_at_ms,
    })
}

pub fn device_record_signing_bytes(record: &DeviceRecord) -> Result<Vec<u8>, serde_json::Error> {
    serde_jcs::to_vec(&DeviceRecordSigningView {
        protocol_version: &record.protocol_version,
        chain_id: &record.chain_id,
        device_id: &record.device_id,
        device_public_key: &record.device_public_key,
        encrypted_device_name: &record.encrypted_device_name,
        device_name_nonce: &record.device_name_nonce,
        status: &record.status,
        created_at_ms: record.created_at_ms,
        updated_at_ms: record.updated_at_ms,
    })
}

pub fn capability_token_signing_bytes(
    token: &CapabilityToken,
) -> Result<Vec<u8>, serde_json::Error> {
    serde_jcs::to_vec(&CapabilityTokenSigningView {
        v: token.v,
        chain_id: &token.chain_id,
        device_id: &token.device_id,
        method: &token.method,
        path: &token.path,
        query: &token.query,
        body_sha256: &token.body_sha256,
        issued_at_ms: token.issued_at_ms,
        expires_at_ms: token.expires_at_ms,
        nonce: &token.nonce,
    })
}

pub fn invitation_bundle_signing_bytes(
    bundle: &InvitationBundle,
) -> Result<Vec<u8>, serde_json::Error> {
    serde_jcs::to_vec(&InvitationBundleSigningView {
        protocol_version: &bundle.protocol_version,
        chain_id: &bundle.chain_id,
        chain_salt: &bundle.chain_salt,
        server_url: &bundle.server_url,
        invitation_id: &bundle.invitation_id,
        issued_at_ms: bundle.issued_at_ms,
        expires_at_ms: bundle.expires_at_ms,
        one_time: bundle.one_time,
        inviter_device_id: &bundle.inviter_device_id,
        inviter_public_key: &bundle.inviter_public_key,
    })
}
