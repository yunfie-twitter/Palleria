//! §§5.5/7.11: protected recovery artifacts cannot enter ordinary persistence or sync.
//! Only reserved type markers/input shapes are checked; no recovery ciphertext is opened.
use crate::client_error::{ClientError, Result};
use serde_json::Value;

pub fn check(value: &Value) -> Result {
    let mut pending = vec![value];
    while let Some(value) = pending.pop() {
        match value {
            Value::Object(object) => {
                let artifact = object
                    .get("type")
                    .and_then(Value::as_str)
                    .is_some_and(|kind| {
                        matches!(
                            kind,
                            "PALLASYNC-RECOVERY-BLOB-v3"
                                | "PALLASYNC-RECOVERY-SET-v3"
                                | "PALLASYNC-RECOVERY-STATUS-v3"
                                | "PALLASYNC-RECOVERY-SHARE-v3"
                                | "PALLASYNC-RECOVERY-PACKAGE-v3"
                                | "PALLASYNC-OFFLINE-SHARE-KEY-v3"
                                | "PALLASYNC-RECOVERY-SESSION-v3"
                                | "PALLASYNC-RECOVERY-TRANSFER-v3"
                        )
                    });
                let root_input =
                    object.contains_key("mnemonic") && object.contains_key("passphrase");
                if artifact || root_input {
                    return Err(ClientError("C_SCHEMA_VIOLATION"));
                }
                pending.extend(object.values());
            }
            Value::Array(values) => pending.extend(values),
            Value::String(encoded) if encoded.starts_with("PSR3:") => {
                if let Ok(package) = crate::recovery::parse_portable(encoded) {
                    let package = crate::recovery::SecretJson(package);
                    if package.0["type"] == "PALLASYNC-RECOVERY-PACKAGE-v3" {
                        return Err(ClientError("C_SCHEMA_VIOLATION"));
                    }
                }
            }
            _ => {}
        }
    }
    Ok(())
}
