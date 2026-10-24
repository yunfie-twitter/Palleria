//! Internal errors carry only literal §19.3 codes, never secret-bearing diagnostics.
use std::fmt;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct ClientError(pub &'static str);

pub type Result<T = ()> = std::result::Result<T, ClientError>;

pub fn err(code: &'static str) -> ClientError {
    ClientError(match code {
        "OBJECT_SIGNATURE_INVALID" => "C_SIGNATURE_INVALID",
        _ => "C_SCHEMA_VIOLATION",
    })
}

impl fmt::Display for ClientError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.0)
    }
}

impl std::error::Error for ClientError {}
