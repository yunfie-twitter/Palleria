//! Protocol §4: pure Ed25519, canonical prime-order A/R, no legacy fallback.
use super::jcs::canonicalize_json;
use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use curve25519_dalek::{edwards::CompressedEdwardsY, scalar::Scalar, traits::IsIdentity};
use ed25519_dalek::{Signature, Signer, SigningKey, VerifyingKey};
use serde_json::Value;
use sha2::{Digest, Sha256};

pub const CTX_SYNC_RECORD: &[u8] = b"PALLASYNC-SYNC-RECORD-v3\0";
pub const CTX_DEVICE_RECORD: &[u8] = b"PALLASYNC-DEVICE-RECORD-v3\0";
pub const CTX_ENROLLMENT: &[u8] = b"PALLASYNC-ENROLLMENT-v3\0";
pub const CTX_CAPABILITY: &[u8] = b"PALLASYNC-CAPABILITY-v3\0";
pub const CTX_PARAMETERS: &[u8] = b"PALLASYNC-CHAIN-PARAMS-v3\0";
pub const CTX_EPOCH: &[u8] = b"PALLASYNC-EPOCH-v3\0";
pub const CTX_ENVELOPE: &[u8] = b"PALLASYNC-EPOCH-ENVELOPE-v3\0";
pub const CTX_COMPACTION: &[u8] = b"PALLASYNC-COMPACTION-v3\0";
pub const CTX_REQUEST: &[u8] = b"PALLASYNC-ENROLL-REQUEST-v3\0";
pub const CTX_ADMIN_OP: &[u8] = b"PALLASYNC-ADMIN-OP-v3\0";
pub const CTX_RECEIPT: &[u8] = b"PALLASYNC-UPLOAD-RECEIPT-v3\0";
pub const CTX_TRANSPARENCY: &[u8] = b"PALLASYNC-TRANSPARENCY-ENTRY-v3\0";
pub const CTX_HEAD: &[u8] = b"PALLASYNC-TRANSPARENCY-HEAD-v3\0";
pub const CTX_DELETE: &[u8] = b"PALLASYNC-DELETE-RECEIPT-v3\0";
pub const CTX_DELETION_WITNESS: &[u8] = b"PALLASYNC-DELETION-WITNESS-v3\0";
pub const CTX_PRIVACY: &[u8] = b"PALLASYNC-RELAY-PRIVACY-v3\0";

pub fn decode(s: &str, length: Option<usize>) -> Result<Vec<u8>, &'static str> {
    if !s
        .bytes()
        .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
    {
        return Err("Noncanonical Base64URL");
    }
    let bytes = URL_SAFE_NO_PAD
        .decode(s)
        .map_err(|_| "Noncanonical Base64URL")?;
    if length.is_some_and(|n| bytes.len() != n) || URL_SAFE_NO_PAD.encode(&bytes) != s {
        return Err("Invalid binary length or trailing bits");
    }
    Ok(bytes)
}
pub fn prime_point(bytes: &[u8; 32]) -> bool {
    CompressedEdwardsY(*bytes).decompress().is_some_and(|p| {
        p.compress().as_bytes() == bytes && !p.is_identity() && p.is_torsion_free()
    })
}
pub fn valid_ed_key(key: &str) -> bool {
    decode(key, Some(32)).is_ok_and(|b| prime_point(b.as_slice().try_into().unwrap()))
}
pub fn valid_x_key(key: &str) -> bool {
    let Ok(b) = decode(key, Some(32)) else {
        return false;
    };
    let mut p = [255u8; 32];
    p[0] = 237;
    p[31] = 127;
    // A fixed nonsecret scalar delegates low-order/all-zero detection to the vetted DH primitive.
    if !b.iter().rev().cmp(p.iter().rev()).is_lt() {
        return false;
    }
    let point = curve25519_dalek::montgomery::MontgomeryPoint(b.as_slice().try_into().unwrap());
    point
        .mul_clamped([42u8; 32])
        .as_bytes()
        .iter()
        .any(|&v| v != 0)
}
pub fn verify_signature(key: &str, sig: &str, message: &[u8]) -> Result<bool, &'static str> {
    let a = decode(key, Some(32))?;
    let s = decode(sig, Some(64))?;
    if !prime_point(a.as_slice().try_into().unwrap())
        || !prime_point(s[..32].try_into().unwrap())
        || !bool::from(Scalar::from_canonical_bytes(s[32..].try_into().unwrap()).is_some())
    {
        return Ok(false);
    }
    let key =
        VerifyingKey::from_bytes(a.as_slice().try_into().unwrap()).map_err(|_| "Invalid key")?;
    Ok(key
        .verify_strict(
            message,
            &Signature::from_bytes(s.as_slice().try_into().unwrap()),
        )
        .is_ok())
}
pub fn signing_bytes(value: &Value, context: &[u8], member: &str) -> Vec<u8> {
    let mut unsigned = value.clone();
    unsigned
        .as_object_mut()
        .expect("validated object")
        .remove(member);
    let mut message = context.to_vec();
    message.extend(canonicalize_json(&unsigned).expect("validated I-JSON"));
    message
}
pub fn verify_signed_json(
    key: &str,
    sig: &str,
    value: &Value,
    context: &[u8],
) -> Result<bool, String> {
    if !value.is_object() {
        return Err("Expected signed object".into());
    }
    verify_signature(key, sig, &signing_bytes(value, context, "signature")).map_err(str::to_owned)
}
pub fn sign_relay(key: &SigningKey, value: &mut Value, context: &[u8]) -> Result<(), &'static str> {
    let input = signing_bytes(value, context, "relay_signature");
    let sig = URL_SAFE_NO_PAD.encode(key.sign(&input).to_bytes());
    if !verify_signature(
        &URL_SAFE_NO_PAD.encode(key.verifying_key().as_bytes()),
        &sig,
        &input,
    )? {
        return Err("Degenerate relay signature");
    }
    value["relay_signature"] = Value::String(sig);
    Ok(())
}
pub fn hash_bytes(bytes: &[u8]) -> String {
    URL_SAFE_NO_PAD.encode(Sha256::digest(bytes))
}
pub fn object_hash(value: &Value) -> String {
    hash_bytes(&canonicalize_json(value).expect("I-JSON"))
}
