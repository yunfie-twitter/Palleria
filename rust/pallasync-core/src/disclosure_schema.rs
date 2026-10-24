use crate::client_error::{Result, err};
use crate::strict::verify::decode;
use serde_json::Value;
pub fn valid_url(s: &str) -> bool {
    s.is_ascii()
        && s.len() <= 4096
        && url::Url::parse(s).is_ok_and(|u| {
            u.scheme() == "https"
                && u.host_str().is_some()
                && u.username().is_empty()
                && u.password().is_none()
                && u.fragment().is_none()
        })
}
pub fn valid_origin(s: &str) -> bool {
    valid_url(s)
        && url::Url::parse(s).is_ok_and(|u| {
            u.path() == "/" && u.query().is_none() && u.origin().ascii_serialization() == s
        })
}
pub(crate) fn validate_strings(v: &Value) -> Result<()> {
    match v {
        Value::String(s) => {
            if s.len() > 4096 || s.chars().count() > 4096 {
                return Err(err("INVALID_FIELD"));
            }
        }
        Value::Object(o) => {
            for v in o.values() {
                validate_strings(v)?
            }
        }
        Value::Array(a) => {
            for v in a {
                validate_strings(v)?
            }
        }
        _ => {}
    }
    Ok(())
}
// Closed evaluator for the exact vendored schema (no remote refs or coercion).
pub(crate) fn schema_valid(root: &Value, schema: &Value, v: &Value) -> bool {
    if let Some(r) = schema["$ref"].as_str() {
        return root
            .pointer(&r[1..])
            .is_some_and(|s| schema_valid(root, s, v));
    }
    for (k, count) in [("anyOf", false), ("oneOf", true)] {
        if let Some(a) = schema[k].as_array() {
            let n = a.iter().filter(|s| schema_valid(root, s, v)).count();
            if (count && n != 1) || (!count && n == 0) {
                return false;
            }
        }
    }
    if schema.get("const").is_some_and(|c| c != v)
        || schema["enum"].as_array().is_some_and(|a| !a.contains(v))
    {
        return false;
    }
    if let Some(t) = schema["type"].as_str()
        && !match t {
            "object" => v.is_object(),
            "array" => v.is_array(),
            "integer" => v.as_u64().is_some(),
            "string" => v.is_string(),
            "boolean" => v.is_boolean(),
            "null" => v.is_null(),
            _ => false,
        }
    {
        return false;
    }
    if let Some(n) = v.as_u64()
        && (schema["minimum"].as_u64().is_some_and(|min| n < min)
            || schema["maximum"].as_u64().is_some_and(|max| n > max))
    {
        return false;
    }
    if let Some(s) = v.as_str() {
        let n = s.chars().count() as u64;
        if schema["minLength"].as_u64().is_some_and(|min| n < min)
            || schema["maxLength"].as_u64().is_some_and(|max| n > max)
            || schema["pattern"]
                .as_str()
                .is_some_and(|p| !regex::Regex::new(p).unwrap().is_match(s))
        {
            return false;
        }
        if schema["pattern"] == "^[A-Za-z0-9_-]{43}$" && decode(s, Some(32)).is_err() {
            return false;
        }
        if schema["pattern"] == "^[A-Za-z0-9_-]{86}$" && decode(s, Some(64)).is_err() {
            return false;
        }
    }
    if let Some(o) = v.as_object() {
        if schema["required"]
            .as_array()
            .is_some_and(|a| a.iter().any(|k| !o.contains_key(k.as_str().unwrap())))
        {
            return false;
        }
        for (k, v) in o {
            if let Some(p) = schema["properties"].get(k) {
                if !schema_valid(root, p, v) {
                    return false;
                }
            } else if schema["additionalProperties"] == false {
                return false;
            }
        }
    }
    if let Some(a) = v.as_array() {
        let n = a.len() as u64;
        if schema["minItems"].as_u64().is_some_and(|min| n < min)
            || schema["maxItems"].as_u64().is_some_and(|max| n > max)
        {
            return false;
        }
        for (i, v) in a.iter().enumerate() {
            if schema
                .get("items")
                .is_some_and(|s| !schema_valid(root, s, v))
                || (schema["uniqueItems"] == true && a[..i].contains(v))
            {
                return false;
            }
        }
    }
    true
}
