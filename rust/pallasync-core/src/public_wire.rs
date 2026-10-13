//! Closed wire schemas from §7; encrypted client schemas never enter this module.
use crate::client_error::{Result, err};
use crate::strict::{jcs::MAX_INTEGER, verify::*};
use serde_json::Value;
use unicode_normalization::UnicodeNormalization;

pub fn s<'a>(v: &'a Value, key: &str) -> &'a str {
    v[key].as_str().unwrap_or("")
}
pub fn n(v: &Value, key: &str) -> u64 {
    v[key].as_u64().unwrap_or(0)
}
pub fn a<'a>(v: &'a Value, key: &str) -> &'a [Value] {
    v[key].as_array().map(Vec::as_slice).unwrap_or(&[])
}
pub fn id(v: &str) -> bool {
    !v.contains('\0') && v.nfc().eq(v.chars())
}
pub fn uuid(v: &str) -> bool {
    uuid::Uuid::parse_str(v).is_ok_and(|u| {
        u.get_version_num() == 4 && u.get_variant() == uuid::Variant::RFC4122 && u.to_string() == v
    })
}
pub fn check_type(v: &Value, t: &str) -> bool {
    if let Some(t) = t.strip_suffix('?') {
        return v.is_null() || check_type(v, t);
    }
    if let Some(t) = t.strip_suffix("[]") {
        return v
            .as_array()
            .is_some_and(|a| a.iter().all(|v| check_type(v, t)));
    }
    match t {
        "string" => v.as_str().is_some_and(id),
        "text" => v.is_string(),
        "uuid" => v.as_str().is_some_and(uuid),
        "u32" => v.as_u64().is_some_and(|n| n <= u32::MAX as u64),
        "int" => v.as_u64().is_some_and(|n| n <= MAX_INTEGER),
        "bool" => v.is_boolean(),
        "object" => v.is_object(),
        "array" => v.is_array(),
        "3.0" => v == "3.0",
        "b16" | "b24" | "b32" | "b48" | "b64" => v
            .as_str()
            .is_some_and(|s| decode(s, Some(t[1..].parse().unwrap())).is_ok()),
        _ => v.as_str().is_some_and(|s| t.split('|').any(|t| t == s)),
    }
}
pub fn fields(v: &Value, required: &[(&str, &str)], optional: &[(&str, &str)]) -> Result<()> {
    let o = v.as_object().ok_or_else(|| err("INVALID_FIELD"))?;
    for (k, t) in required {
        if !o.get(*k).is_some_and(|v| check_type(v, t)) {
            return Err(err("INVALID_FIELD"));
        }
    }
    for (k, v) in o {
        let Some((_, t)) = required.iter().chain(optional).find(|(name, _)| name == k) else {
            return Err(err("INVALID_FIELD"));
        };
        if !check_type(v, t) {
            return Err(err("INVALID_FIELD"));
        }
    }
    Ok(())
}
pub fn signed(v: &Value, required: &[(&str, &str)]) -> Result<()> {
    let mut required = required.to_vec();
    required.push(("signature", "b64"));
    fields(v, &required, &[("ext", "object")])?;
    if let Some(ext) = v.get("ext") {
        if crate::strict::jcs::canonicalize_json(ext).unwrap().len() > 1024 {
            return Err(err("INVALID_FIELD"));
        }
        for key in ext.as_object().unwrap().keys() {
            let labels: Vec<_> = key.split('.').collect();
            if labels.len() < 3
                || key.len() > 255
                || !labels.iter().enumerate().all(|(i, label)| {
                    let last = i + 1 == labels.len();
                    !label.is_empty()
                        && label.len() <= if last { 64 } else { 63 }
                        && label.as_bytes()[0].is_ascii_lowercase()
                        && !label.ends_with('-')
                        && label.bytes().all(|b| {
                            b.is_ascii_lowercase()
                                || b.is_ascii_digit()
                                || if last { b == b'_' } else { b == b'-' }
                        })
                })
            {
                return Err(err("INVALID_FIELD"));
            }
        }
    }
    Ok(())
}
pub fn signer(v: &Value) -> Result<()> {
    if (s(v, "signer_kind") == "admin") != v["signer_device_id"].is_null() {
        return Err(err("INVALID_FIELD"));
    }
    Ok(())
}
pub fn verify(v: &Value, key: &str, ctx: &[u8]) -> Result<()> {
    if !verify_signed_json(key, s(v, "signature"), v, ctx).unwrap_or(false) {
        return Err(err("OBJECT_SIGNATURE_INVALID"));
    }
    Ok(())
}
pub fn validate(v: &Value, kind: &str) -> Result<()> {
    crate::normal_data_boundary::check(v).map_err(|_| err("INVALID_FIELD"))?;
    match kind {
        "parameters" => {
            signed(
                v,
                &[
                    ("version", "3.0"),
                    ("chain_id", "b32"),
                    ("admin_public_key", "b32"),
                    ("generation", "u32"),
                    ("previous_parameters_hash", "b32?"),
                    ("policy", "object"),
                ],
            )?;
            fields(
                &v["policy"],
                &[
                    ("metadata_profile", "private|queryable"),
                    ("allow_device_chain_delete", "bool"),
                    ("allow_peer_enrollment", "bool"),
                    ("allow_device_rotation", "bool"),
                    ("epoch_retention", "retain|windowed|immediate"),
                    ("epoch_window_ms", "int"),
                    ("scheduled_rotation_ms", "int"),
                ],
                &[],
            )?;
            let w = n(&v["policy"], "epoch_window_ms");
            let t = n(&v["policy"], "scheduled_rotation_ms");
            if !(1..=31_536_000_000).contains(&w)
                || (t != 0 && !(3_600_000..=31_536_000_000).contains(&t))
                || (n(v, "generation") == 0) != v["previous_parameters_hash"].is_null()
                || !valid_ed_key(s(v, "admin_public_key"))
            {
                return Err(err("INVALID_FIELD"));
            }
        }
        "certificate" => {
            signed(
                v,
                &[
                    ("chain_id", "b32"),
                    ("generation", "u32"),
                    ("certificate_id", "uuid"),
                    ("device_id", "uuid"),
                    ("device_public_key", "b32"),
                    ("device_kex_public_key", "b32"),
                    ("signer_kind", "admin|device"),
                    ("signer_device_id", "uuid?"),
                    ("request_hash", "b32?"),
                    ("approved_at_ms", "int"),
                ],
            )?;
            signer(v)?;
            if (s(v, "signer_kind") == "device" && v["request_hash"].is_null())
                || !valid_ed_key(s(v, "device_public_key"))
                || !valid_x_key(s(v, "device_kex_public_key"))
            {
                return Err(err("INVALID_FIELD"));
            }
        }
        "device" => {
            signed(
                v,
                &[
                    ("version", "3.0"),
                    ("chain_id", "b32"),
                    ("generation", "u32"),
                    ("device_id", "uuid"),
                    ("device_public_key", "b32"),
                    ("device_kex_public_key", "b32"),
                    ("meta_epoch", "u32"),
                    ("meta_nonce", "b24"),
                    ("encrypted_device_meta", "string"),
                    ("enrollment", "object"),
                ],
            )?;
            validate(&v["enrollment"], "certificate")?;
            let b =
                decode(s(v, "encrypted_device_meta"), None).map_err(|_| err("INVALID_FIELD"))?;
            if ![1040, 4112].contains(&b.len()) {
                return Err(err("INVALID_FIELD"));
            }
            for k in [
                "chain_id",
                "generation",
                "device_id",
                "device_public_key",
                "device_kex_public_key",
            ] {
                if v[k] != v["enrollment"][k] {
                    return Err(err("ENROLLMENT_CERTIFICATE_INVALID"));
                }
            }
        }
        "epoch" => {
            signed(
                v,
                &[
                    ("chain_id", "b32"),
                    ("generation", "u32"),
                    ("epoch", "u32"),
                    ("previous_epoch_hash", "b32?"),
                    ("epoch_commitment", "b32"),
                    (
                        "reason",
                        "genesis|scheduled|manual|device_revocation|key_rotation|reseed|emergency",
                    ),
                    ("signer_kind", "admin|device"),
                    ("signer_device_id", "uuid?"),
                    ("members", "uuid[]"),
                    ("revoked_device_ids", "uuid[]"),
                    ("recovery_envelope_hash", "b32"),
                ],
            )?;
            signer(v)?;
            if a(v, "members").is_empty()
                || a(v, "members").len() > 128
                || (n(v, "epoch") == 0) != v["previous_epoch_hash"].is_null()
            {
                return Err(err("EPOCH_CHAIN_INVALID"));
            }
            for k in ["members", "revoked_device_ids"] {
                if a(v, k).windows(2).any(|w| w[0].as_str() >= w[1].as_str()) {
                    return Err(err("MEMBER_SET_MISMATCH"));
                }
            }
            if a(v, "members")
                .iter()
                .any(|id| a(v, "revoked_device_ids").contains(id))
            {
                return Err(err("MEMBER_SET_MISMATCH"));
            }
        }
        "envelope" => {
            signed(
                v,
                &[
                    ("envelope_id", "uuid"),
                    ("chain_id", "b32"),
                    ("generation", "u32"),
                    ("epoch", "u32"),
                    ("previous_epoch_hash", "b32?"),
                    ("epoch_commitment", "b32"),
                    ("recipient_kind", "device|recovery"),
                    ("recipient_device_id", "uuid?"),
                    ("recipient_key_hash", "b32?"),
                    ("signer_kind", "admin|device"),
                    ("signer_device_id", "uuid?"),
                    ("enc", "b32?"),
                    ("nonce", "b24?"),
                    ("ciphertext", "b48"),
                ],
            )?;
            signer(v)?;
            let device = s(v, "recipient_kind") == "device";
            if device == v["recipient_device_id"].is_null()
                || device == v["recipient_key_hash"].is_null()
                || device == v["enc"].is_null()
                || device != v["nonce"].is_null()
                || (device && !valid_x_key(s(v, "enc")))
            {
                return Err(err("INVALID_FIELD"));
            }
        }
        "record" => {
            signed(
                v,
                &[
                    ("version", "3.0"),
                    ("chain_id", "b32"),
                    ("generation", "u32"),
                    ("record_id", "uuid"),
                    ("device_id", "uuid"),
                    ("epoch", "u32"),
                    ("collection_tag", "b16?"),
                    ("payload_nonce", "b24"),
                    ("encrypted_payload", "string"),
                ],
            )?;
            let b = decode(s(v, "encrypted_payload"), None).map_err(|_| err("INVALID_FIELD"))?;
            if ![1040, 4112, 16400, 65552, 262160, 1048592].contains(&b.len()) {
                return Err(err("INVALID_FIELD"));
            }
        }
        "compaction" => {
            signed(
                v,
                &[
                    ("compaction_id", "uuid"),
                    ("chain_id", "b32"),
                    ("generation", "u32"),
                    ("device_id", "uuid"),
                    ("log_id", "uuid"),
                    ("covered_through_seq", "int"),
                    ("covered_log_hash", "b32"),
                    ("epoch_hash", "b32"),
                    ("snapshot_parts", "array"),
                    ("state_sha256", "b32"),
                ],
            )?;
            if a(v, "snapshot_parts").is_empty() || a(v, "snapshot_parts").len() > 65536 {
                return Err(err("INVALID_FIELD"));
            }
            let mut ids = std::collections::HashSet::new();
            for p in a(v, "snapshot_parts") {
                fields(p, &[("record_id", "uuid"), ("record_hash", "b32")], &[])?;
                if !ids.insert(s(p, "record_id")) {
                    return Err(err("INVALID_FIELD"));
                }
            }
        }
        "admin" => {
            signed(
                v,
                &[
                    ("operation_id", "uuid"),
                    ("chain_id", "b32"),
                    ("generation", "u32"),
                    (
                        "action",
                        "approve_enrollment|revoke_device|reseed|delete_chain|prune_log",
                    ),
                    ("expected_parameters_hash", "b32"),
                    ("expected_epoch_hash", "b32"),
                    ("target_hash", "b32"),
                    ("created_at_ms", "int"),
                ],
            )?;
        }
        "request" => {
            signed(
                v,
                &[
                    ("request_id", "uuid"),
                    ("chain_id", "b32"),
                    ("generation", "u32"),
                    ("device_id", "uuid"),
                    ("device_public_key", "b32"),
                    ("device_kex_public_key", "b32"),
                    ("offer_nonce", "b32"),
                    ("created_at_ms", "int"),
                    ("expires_at_ms", "int"),
                ],
            )?;
            if n(v, "created_at_ms") >= n(v, "expires_at_ms")
                || n(v, "expires_at_ms") - n(v, "created_at_ms") > 300000
                || !valid_ed_key(s(v, "device_public_key"))
                || !valid_x_key(s(v, "device_kex_public_key"))
            {
                return Err(err("INVALID_FIELD"));
            }
        }
        _ => unreachable!("closed schema kind"),
    }
    Ok(())
}

pub fn canonical_query(raw: &str) -> Result<Vec<(String, String)>> {
    fn pct(s: &str) -> Result<String> {
        let b = s.as_bytes();
        let mut out = String::new();
        let mut i = 0;
        while i < b.len() {
            let v = if b[i] == b'%' {
                if i + 2 >= b.len() {
                    return Err(err("INVALID_FIELD"));
                }
                let h = std::str::from_utf8(&b[i + 1..i + 3]).map_err(|_| err("INVALID_FIELD"))?;
                i += 2;
                u8::from_str_radix(h, 16).map_err(|_| err("INVALID_FIELD"))?
            } else {
                b[i]
            };
            if v.is_ascii_alphanumeric() || b"-._~".contains(&v) {
                out.push(v as char)
            } else {
                out.push_str(&format!("%{v:02X}"))
            }
            i += 1;
        }
        Ok(out)
    }
    let mut out = Vec::new();
    if raw.is_empty() {
        return Ok(out);
    }
    for p in raw.split('&') {
        let (k, v) = p.split_once('=').unwrap_or((p, ""));
        out.push((pct(k)?, pct(v)?))
    }
    out.sort();
    if out.windows(2).any(|w| w[0].0 == w[1].0) {
        return Err(err("INVALID_FIELD"));
    }
    Ok(out)
}
pub fn query_string(q: &[(String, String)]) -> String {
    q.iter()
        .map(|(k, v)| format!("{k}={v}"))
        .collect::<Vec<_>>()
        .join("&")
}
