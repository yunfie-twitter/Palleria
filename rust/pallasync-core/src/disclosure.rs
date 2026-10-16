//! §§7.13/11.7: validate the complete pair before a token or chain request exists.
use crate::{
    client_error::{ClientError, Result},
    disclosure_schema::{schema_valid, valid_origin, valid_url},
    public_wire::{a, n, s},
    strict::{jcs, verify::*},
};
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ValidatedPolicy {
    pub relay_origin: String,
    pub relay_key_id: String,
    pub revision: u32,
    pub policy_sha256: String,
    pub disclosure_sha256: String,
    pub disclosure: Value,
}
fn invalid() -> ClientError {
    ClientError("C_RELAY_DISCLOSURE_INVALID")
}
fn require(ok: bool) -> Result {
    if ok { Ok(()) } else { Err(invalid()) }
}

pub fn validate_health(health: &Value, origin: &str) -> Result {
    crate::public_wire::fields(
        health,
        &[
            ("protocol", "pallasync/3"),
            ("relay_origin", "text"),
            ("server_time_ms", "int"),
            (
                "durability_tier",
                "single-durable|replicated-durable|backup-durable",
            ),
            ("limits", "object"),
            ("shutdown_at_ms", "int?"),
            ("migration_origin", "text?"),
            ("relay_public_key", "b32"),
            ("relay_key_id", "b32"),
        ],
        &[],
    )
    .map_err(|_| invalid())?;
    crate::public_wire::fields(
        &health["limits"],
        &[
            ("max_request_bytes", "int"),
            ("max_records_per_upload", "int"),
            ("page_limit", "int"),
            ("max_devices", "int"),
        ],
        &[],
    )
    .map_err(|_| invalid())?;
    require(
        valid_origin(origin)
            && s(health, "relay_origin") == origin
            && valid_ed_key(s(health, "relay_public_key"))
            && s(health, "relay_key_id")
                == hash_bytes(
                    &decode(s(health, "relay_public_key"), Some(32)).map_err(|_| invalid())?,
                ),
    )?;
    require(health["migration_origin"].is_null() || valid_origin(s(health, "migration_origin")))?;
    for (k, max) in [
        ("max_request_bytes", 8_388_608),
        ("max_records_per_upload", 64),
        ("page_limit", 1000),
        ("max_devices", 128),
    ] {
        require((1..=max).contains(&n(&health["limits"], k)))?;
    }
    Ok(())
}

/// §10.14: validate the exact authenticated per-chain shutdown notice object.
pub fn validate_relay_notice(notice: &Value) -> Result {
    crate::public_wire::fields(
        notice,
        &[
            ("shutdown_at_ms", "int?"),
            ("migration_origin", "text?"),
            ("notice_id", "uuid"),
            ("server_time_ms", "int"),
        ],
        &[],
    )
    .map_err(|_| invalid())?;
    require(notice["migration_origin"].is_null() || valid_origin(s(notice, "migration_origin")))?;
    Ok(())
}

#[cfg(test)]
mod relay_notice_tests {
    use super::validate_relay_notice;
    use serde_json::json;

    #[test]
    fn relay_notice_requires_exact_members_and_normalized_suggestion() {
        let valid = json!({
            "shutdown_at_ms": 1791685000000u64,
            "migration_origin": "https://target.example",
            "notice_id": "f4e3d90e-2461-4c7c-9f2f-89452a28657c",
            "server_time_ms": 1791684000000u64
        });
        assert!(validate_relay_notice(&valid).is_ok());

        let empty = json!({
            "shutdown_at_ms": null,
            "migration_origin": null,
            "notice_id": "f4e3d90e-2461-4c7c-9f2f-89452a28657c",
            "server_time_ms": 1791684000000u64
        });
        assert!(validate_relay_notice(&empty).is_ok());

        let mut unknown = valid.clone();
        unknown["detail"] = json!("ignore me");
        assert!(validate_relay_notice(&unknown).is_err());

        let mut unsafe_origin = valid;
        unsafe_origin["migration_origin"] = json!("https://target.example/path");
        assert!(validate_relay_notice(&unsafe_origin).is_err());
    }
}

#[allow(clippy::too_many_arguments)]
pub fn validate_pair(
    markdown: &[u8],
    json: &[u8],
    health: &Value,
    origin: &str,
    pinned_key: &str,
    highest_revision: u32,
    highest_hash: &str,
    corrected_now_ms: u64,
) -> Result<ValidatedPolicy> {
    crate::public_wire::fields(
        health,
        &[
            ("protocol", "pallasync/3"),
            ("relay_origin", "text"),
            ("server_time_ms", "int"),
            ("durability_tier", "text"),
            ("limits", "object"),
            ("shutdown_at_ms", "int?"),
            ("migration_origin", "text?"),
            ("relay_public_key", "b32"),
            ("relay_key_id", "b32"),
        ],
        &[],
    )
    .map_err(|_| invalid())?;
    crate::public_wire::fields(
        &health["limits"],
        &[
            ("max_request_bytes", "int"),
            ("max_records_per_upload", "int"),
            ("page_limit", "int"),
            ("max_devices", "int"),
        ],
        &[],
    )
    .map_err(|_| invalid())?;
    require(
        markdown.len() <= 1_048_576
            && json.len() <= 1_048_576
            && std::str::from_utf8(markdown).is_ok()
            && valid_origin(origin),
    )?;
    if !valid_ed_key(pinned_key) || s(health, "relay_public_key") != pinned_key {
        return Err(ClientError("C_RELAY_KEY_MISMATCH"));
    }
    let key_id = hash_bytes(&decode(pinned_key, Some(32)).map_err(|_| invalid())?);
    let p = jcs::parse(json).map_err(|_| invalid())?;
    let schema: Value =
        serde_json::from_str(include_str!("../spec/privacy.schema.json")).map_err(|_| invalid())?;
    require(schema_valid(&schema, &schema, &p))?;
    crate::disclosure_schema::validate_strings(&p).map_err(|_| invalid())?;
    require(
        s(&p, "relay_origin") == origin
            && s(&p, "relay_key_id") == key_id
            && s(health, "relay_origin") == origin
            && s(health, "relay_key_id") == key_id
            && s(health, "protocol") == "pallasync/3",
    )?;
    require(
        s(&p, "markdown_sha256") == hash_bytes(markdown)
            && n(&p, "published_at_ms") <= n(&p, "effective_at_ms")
            && n(&p, "effective_at_ms") <= corrected_now_ms,
    )?;
    require(
        verify_signature(
            pinned_key,
            s(&p, "relay_signature"),
            &signing_bytes(&p, CTX_PRIVACY, "relay_signature"),
        )
        .unwrap_or(false),
    )?;
    let hash = object_hash(&p);
    let revision = n(&p, "revision");
    require(
        revision >= u64::from(highest_revision)
            && (revision != u64::from(highest_revision)
                || highest_hash.is_empty()
                || hash == highest_hash),
    )?;
    require(p["limits"] == health["limits"] && p["durability_tier"] == health["durability_tier"])?;
    for (key, max) in [
        ("max_request_bytes", 8_388_608),
        ("max_records_per_upload", 64),
        ("page_limit", 1000),
        ("max_devices", 128),
    ] {
        require((1..=max).contains(&n(&p["limits"], key)))?;
    }
    let q = &p["quota"];
    require(q["maximum_bytes"].is_null() || n(q, "maximum_bytes") >= n(q, "minimum_bytes"))?;
    for (scope, rate, burst) in [
        ("authorized_signer", 120, 30),
        ("record_upload_device", 30, 10),
        ("enrollment_source_ip", 6, 3),
        ("enrollment_offer", 3, 1),
    ] {
        require(
            n(&p["rate_limits"][scope], "requests_per_minute") <= rate
                && n(&p["rate_limits"][scope], "burst") <= burst,
        )?;
    }
    require(
        n(
            &p["rate_limits"]["public_document_ip"],
            "requests_per_minute",
        ) >= 2 * n(
            &p["rate_limits"]["authorized_signer"],
            "requests_per_minute",
        ),
    )?;
    let tier = s(&p, "durability_tier");
    for copy in [
        "active_store",
        "snapshot_store",
        "attestations",
        "deletion_ledger",
    ] {
        require(p["copies"][copy]["present"] == true)?;
    }
    require(p["copies"]["backup"]["present"] == p["backups"]["enabled"])?;
    if matches!(tier, "replicated-durable" | "backup-durable") {
        require(p["copies"]["replica"]["present"] == true)?;
    }
    if tier == "backup-durable" {
        require(
            p["backups"]["enabled"] == true
                && n(&p["backups"], "max_interval_ms") <= 86_400_000
                && !p["backups"]["restore_test_max_interval_ms"].is_null()
                && n(&p["backups"], "restore_test_max_interval_ms") <= 7_776_000_000
                && !p["backups"]["last_successful_restore_test_at_ms"].is_null()
                && n(&p["backups"], "last_successful_restore_test_at_ms")
                    <= n(&p, "published_at_ms"),
        )?;
    }
    require(p["copies"]["active_store"]["retention"]["mode"] == "until_chain_deletion")?;
    let deletion = &p["deletion"];
    if p["backups"]["enabled"] == false {
        require(deletion["backup_delete_max_ms"].is_null())?;
    }
    if s(deletion, "assurance") == "backup-expiry" {
        require(
            deletion["key_destroy_max_ms"].is_null()
                && (p["backups"]["enabled"] == false
                    || !deletion["backup_delete_max_ms"].is_null()),
        )?;
    } else {
        require(!deletion["key_destroy_max_ms"].is_null())?;
    }
    for (name, c) in p["copies"].as_object().ok_or_else(invalid)? {
        if c["present"] == false {
            continue;
        }
        let after = &c["after_delete"];
        if c["content"] == "ciphertext" {
            require(
                matches!(s(after, "mode"), "erase" | "crypto_shred") && n(after, "max_ms") >= 1,
            )?;
            if s(deletion, "assurance") == "backup-expiry" {
                require(after["mode"] == "erase")?;
            }
            if after["mode"] == "crypto_shred" {
                require(n(after, "max_ms") <= n(deletion, "key_destroy_max_ms"))?;
            }
            if matches!(
                name.as_str(),
                "active_store" | "replica" | "wal" | "snapshot_store"
            ) {
                require(n(after, "max_ms") <= n(deletion, "active_delete_max_ms"))?;
            }
            if name == "backup" {
                require(
                    !deletion["backup_delete_max_ms"].is_null()
                        && n(after, "max_ms") <= n(deletion, "backup_delete_max_ms"),
                )?;
            }
        }
    }
    if a(&p["metadata"], "retained_fields").is_empty() {
        require(
            a(&p["metadata"], "purposes").is_empty()
                && a(&p["metadata"], "recipients").is_empty()
                && p["metadata"]["retention"]["mode"] == "not_retained",
        )?;
    }
    for path in [
        "/operator/contact_url",
        "/change_notice/notice_url",
        "/change_notice/archive_url",
    ] {
        require(
            p.pointer(path)
                .and_then(Value::as_str)
                .is_some_and(valid_url),
        )?;
    }
    Ok(ValidatedPolicy {
        relay_origin: origin.into(),
        relay_key_id: key_id,
        revision: revision as u32,
        policy_sha256: hash_bytes(markdown),
        disclosure_sha256: hash,
        disclosure: p,
    })
}

pub fn validate_quota(policy: &Value, maximum: &Value) -> Result {
    let q = &policy["quota"];
    if maximum.is_null() {
        return require(q["allow_unconfigured"] == true);
    }
    let value = maximum.as_u64().ok_or_else(invalid)?;
    require(
        value >= n(q, "minimum_bytes")
            && (q["maximum_bytes"].is_null() || value <= n(q, "maximum_bytes")),
    )
}
