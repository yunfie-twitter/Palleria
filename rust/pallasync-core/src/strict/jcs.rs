//! §3.1 duplicate-detecting parser; lexical number checks precede conversion.
use serde::{
    Deserialize,
    de::{self, MapAccess, SeqAccess, Visitor},
};
use serde_json::{Map, Value};
use std::fmt;
pub const MAX_INTEGER: u64 = 9_007_199_254_740_991;
struct Strict(Value);
impl<'de> Deserialize<'de> for Strict {
    fn deserialize<D: de::Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        struct V;
        impl<'de> Visitor<'de> for V {
            type Value = Strict;
            fn expecting(&self, f: &mut fmt::Formatter) -> fmt::Result {
                f.write_str("I-JSON")
            }
            fn visit_bool<E: de::Error>(self, v: bool) -> Result<Strict, E> {
                Ok(Strict(v.into()))
            }
            fn visit_unit<E: de::Error>(self) -> Result<Strict, E> {
                Ok(Strict(Value::Null))
            }
            fn visit_str<E: de::Error>(self, v: &str) -> Result<Strict, E> {
                Ok(Strict(v.into()))
            }
            fn visit_string<E: de::Error>(self, v: String) -> Result<Strict, E> {
                Ok(Strict(v.into()))
            }
            fn visit_u64<E: de::Error>(self, v: u64) -> Result<Strict, E> {
                if v > MAX_INTEGER {
                    Err(E::custom("Integer exceeds safe range"))
                } else {
                    Ok(Strict(v.into()))
                }
            }
            fn visit_i64<E: de::Error>(self, v: i64) -> Result<Strict, E> {
                if v < 0 {
                    Err(E::custom("Negative integer"))
                } else {
                    self.visit_u64(v as u64)
                }
            }
            fn visit_f64<E: de::Error>(self, _: f64) -> Result<Strict, E> {
                Err(E::custom("Float forbidden"))
            }
            fn visit_seq<A: SeqAccess<'de>>(self, mut a: A) -> Result<Strict, A::Error> {
                let mut items = Vec::new();
                while let Some(Strict(v)) = a.next_element()? {
                    items.push(v)
                }
                Ok(Strict(Value::Array(items)))
            }
            fn visit_map<A: MapAccess<'de>>(self, mut a: A) -> Result<Strict, A::Error> {
                let mut obj = Map::new();
                while let Some(k) = a.next_key::<String>()? {
                    if obj.contains_key(&k) {
                        return Err(de::Error::custom("Duplicate member"));
                    }
                    let Strict(v) = a.next_value()?;
                    obj.insert(k, v);
                }
                Ok(Strict(Value::Object(obj)))
            }
        }
        d.deserialize_any(V)
    }
}
pub fn parse(bytes: &[u8]) -> Result<Value, &'static str> {
    let s = std::str::from_utf8(bytes).map_err(|_| "Invalid UTF-8")?;
    if bytes.starts_with(&[239, 187, 191]) {
        return Err("BOM forbidden");
    }
    let mut quoted = false;
    let mut escaped = false;
    let mut depth = 0usize;
    let mut i = 0;
    while i < bytes.len() {
        let b = bytes[i];
        if quoted {
            if escaped {
                escaped = false
            } else if b == b'\\' {
                escaped = true
            } else if b == b'"' {
                quoted = false
            }
        } else {
            match b {
                b'"' => quoted = true,
                b'{' | b'[' => {
                    depth += 1;
                    if depth > 32 {
                        return Err("Depth exceeds 32");
                    }
                }
                b'}' | b']' => depth = depth.saturating_sub(1),
                b'-' => return Err("Negative number forbidden"),
                b'0'..=b'9' => {
                    let start = i;
                    while i < bytes.len() && bytes[i].is_ascii_digit() {
                        i += 1
                    }
                    if i - start > 1 && bytes[start] == b'0' {
                        return Err("Leading zero");
                    }
                    if i < bytes.len() && matches!(bytes[i], b'.' | b'e' | b'E') {
                        return Err("Float/exponent forbidden");
                    }
                    continue;
                }
                _ => {}
            }
        }
        i += 1;
    }
    serde_json::from_str::<Strict>(s)
        .map(|v| v.0)
        .map_err(|_| "Invalid I-JSON")
}
pub fn canonicalize_json(value: &Value) -> Result<Vec<u8>, serde_json::Error> {
    serde_jcs::to_vec(value)
}
