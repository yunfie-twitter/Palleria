//! Local public checkpoint signing and verification (§7.12.4/§17.10).
use crate::{
    client_error::{ClientError, Result},
    strict::{
        jcs,
        verify::{self, CTX_CHECKPOINT},
    },
};
use base64::{Engine, engine::general_purpose::URL_SAFE_NO_PAD};
use ed25519_dalek::SigningKey;
use serde_json::{Value, json};

const REQUIRED: [(&str, &str); 17] = [
    ("type", "string"),
    ("version", "3.0"),
    ("checkpoint_id", "uuid"),
    ("chain_id", "b32"),
    ("generation", "u32"),
    ("device_id", "uuid"),
    ("relay_origin", "string"),
    ("relay_key_id", "b32"),
    ("log_id", "uuid"),
    ("seq", "int"),
    ("head_hash", "b32"),
    ("parameters_hash", "b32"),
    ("epoch_hash", "b32"),
    ("transparency_id", "uuid"),
    ("transparency_index", "int"),
    ("transparency_hash", "b32"),
    ("created_at_ms", "int"),
];
const MAX_SAFE_INTEGER: u64 = 9_007_199_254_740_991;

fn validate_shape(value: &Value, allow_empty_signature: bool) -> Result<()> {
    let object = value.as_object().ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
    crate::public_wire::signed(value, &REQUIRED).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    if object.get("type") != Some(&json!("PALLASYNC-CHECKPOINT-v3")) {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    }
    let text = |name: &str| {
        value[name]
            .as_str()
            .ok_or(ClientError("C_SCHEMA_VIOLATION"))
    };
    if !crate::disclosure_schema::valid_origin(text("relay_origin")?) {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    }
    if ["seq", "transparency_index", "created_at_ms"]
        .iter()
        .any(|name| {
            value[*name]
                .as_u64()
                .is_none_or(|number| number > MAX_SAFE_INTEGER)
        })
    {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    }
    if allow_empty_signature && text("signature")? != URL_SAFE_NO_PAD.encode([0u8; 64]) {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    }
    Ok(())
}

pub fn sign(value: &mut Value, seed: &[u8], certified_public_key: &str) -> Result<()> {
    if seed.len() != 32 {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    }
    validate_shape(value, true)?;
    if value["signature"] != URL_SAFE_NO_PAD.encode([0u8; 64]) {
        return Err(ClientError("C_SCHEMA_VIOLATION"));
    }
    let key_bytes = zeroize::Zeroizing::new(
        seed.try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
    );
    let key = SigningKey::from_bytes(&key_bytes);
    if verify::decode(certified_public_key, Some(32))
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?
        .as_slice()
        != key.verifying_key().as_bytes()
    {
        return Err(ClientError("C_DEVICE_KEY_MISMATCH"));
    }
    let mut unsigned = value.clone();
    unsigned
        .as_object_mut()
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))?
        .remove("signature");
    let canonical =
        jcs::canonicalize_json(&unsigned).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    let signature = crate::crypto::sign_with_context(&key, CTX_CHECKPOINT, &canonical);
    value
        .as_object_mut()
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))?
        .insert("signature".into(), json!(signature));
    verify(value, certified_public_key)
}

pub fn verify(value: &Value, certified_public_key: &str) -> Result<()> {
    validate_shape(value, false)?;
    if !verify::verify_signed_json(
        certified_public_key,
        value["signature"].as_str().unwrap_or(""),
        value,
        CTX_CHECKPOINT,
    )
    .unwrap_or(false)
    {
        return Err(ClientError("C_SIGNATURE_INVALID"));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::SigningKey;

    fn template() -> Value {
        json!({
            "type":"PALLASYNC-CHECKPOINT-v3","version":"3.0",
            "checkpoint_id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            "chain_id":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","generation":4,
            "device_id":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","relay_origin":"https://relay.example.com",
            "relay_key_id":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "log_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd","seq":19,
            "head_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "parameters_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "epoch_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "transparency_id":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","transparency_index":23,
            "transparency_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "created_at_ms":1791684000000i64,
            "signature":URL_SAFE_NO_PAD.encode([0u8;64]),
        })
    }

    #[test]
    fn signs_and_verifies_exact_checkpoint_fields_and_context() {
        let seed = [7u8; 32];
        let public =
            URL_SAFE_NO_PAD.encode(SigningKey::from_bytes(&seed).verifying_key().as_bytes());
        let mut value = template();
        sign(&mut value, &seed, &public).unwrap();
        verify(&value, &public).unwrap();
        value["seq"] = json!(20);
        assert_eq!(
            verify(&value, &public).unwrap_err().0,
            "C_SIGNATURE_INVALID"
        );
    }

    #[test]
    fn rejects_wrong_key_invalid_origin_and_unknown_fields() {
        let seed = [9u8; 32];
        let public =
            URL_SAFE_NO_PAD.encode(SigningKey::from_bytes(&seed).verifying_key().as_bytes());
        let mut wrong_key = template();
        assert_eq!(
            sign(&mut wrong_key, &[8u8; 32], &public).unwrap_err().0,
            "C_DEVICE_KEY_MISMATCH"
        );
        let mut origin = template();
        origin["relay_origin"] = json!("https://relay.example.com/path");
        assert_eq!(
            sign(&mut origin, &seed, &public).unwrap_err().0,
            "C_SCHEMA_VIOLATION"
        );
        let mut extra = template();
        extra["unexpected"] = json!(true);
        assert_eq!(
            sign(&mut extra, &seed, &public).unwrap_err().0,
            "C_SCHEMA_VIOLATION"
        );
    }
}
