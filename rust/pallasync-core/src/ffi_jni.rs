// ffi_jni.rs
// JNI bindings for PallaSync v2.1 Android

use crate::crypto::{self};
use crate::models::{DeviceRecord, SyncRecord};
use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jlong, jstring};
use serde_json::json;
use std::time::{SystemTime, UNIX_EPOCH};

fn read_string<'local>(env: &mut JNIEnv<'local>, value: &JString<'local>) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

fn now_ms() -> Option<i64> {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .ok()
        .map(|duration| duration.as_millis() as i64)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_generateSeedPhrase<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let seed = crypto::generate_seed_phrase();
    match env.new_string(seed) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_deriveKeys<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    seed_phrase: JString<'local>,
) -> jstring {
    let Some(seed_str) = read_string(&mut env, &seed_phrase) else {
        return std::ptr::null_mut();
    };
    let derived = match crypto::derive_keys_from_seed(&seed_str) {
        Ok(keys) => keys,
        Err(_) => return std::ptr::null_mut(),
    };

    let enc_key_b64 = URL_SAFE_NO_PAD.encode(&derived.encryption_key);
    let sign_key_b64 = URL_SAFE_NO_PAD.encode(derived.signing_key.to_bytes());
    let pub_key_b64 = URL_SAFE_NO_PAD.encode(derived.signing_key.verifying_key().as_bytes());

    #[derive(serde::Serialize)]
    struct DerivedKeysOutput<'a> {
        chain_id: &'a str,
        chain_salt: &'a str,
        encryption_key: &'a str,
        signing_key: &'a str,
        public_key: &'a str,
    }

    let output = DerivedKeysOutput {
        chain_id: &derived.chain_id,
        chain_salt: &derived.chain_salt,
        encryption_key: &enc_key_b64,
        signing_key: &sign_key_b64,
        public_key: &pub_key_b64,
    };

    let result_str = match serde_json::to_string(&output) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };

    match env.new_string(result_str) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createSyncRecord<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    chain_id: JString<'local>,
    record_id: JString<'local>,
    collection_name: JString<'local>,
    action: JString<'local>,
    payload_json: JString<'local>,
    device_id: JString<'local>,
    encryption_key: JString<'local>,
    signing_key: JString<'local>,
    lamport: jlong,
) -> jstring {
    let (
        Some(chain_id_str),
        Some(record_id_str),
        Some(collection_name_str),
        Some(action_str),
        Some(payload_str),
        Some(device_id_str),
        Some(encryption_key_str),
        Some(signing_key_str),
    ) = (
        read_string(&mut env, &chain_id),
        read_string(&mut env, &record_id),
        read_string(&mut env, &collection_name),
        read_string(&mut env, &action),
        read_string(&mut env, &payload_json),
        read_string(&mut env, &device_id),
        read_string(&mut env, &encryption_key),
        read_string(&mut env, &signing_key),
    )
    else {
        return std::ptr::null_mut();
    };
    let Ok(encryption_key) = crypto::decode_encryption_key(&encryption_key_str) else {
        return std::ptr::null_mut();
    };
    let Ok(signing_key) = crypto::decode_signing_key(&signing_key_str) else {
        return std::ptr::null_mut();
    };
    let Some(created_at_ms) = now_ms() else {
        return std::ptr::null_mut();
    };
    let Ok(record) = crypto::create_sync_record_at(
        &chain_id_str,
        &record_id_str,
        &collection_name_str,
        &action_str,
        payload_str.as_bytes(),
        &device_id_str,
        &encryption_key,
        &signing_key,
        lamport,
        created_at_ms,
    ) else {
        return std::ptr::null_mut();
    };
    let Ok(serialized) = serde_json::to_string(&record) else {
        return std::ptr::null_mut();
    };
    match env.new_string(serialized) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_decryptSyncRecord<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    record_json: JString<'local>,
    encryption_key: JString<'local>,
) -> jstring {
    let (Some(record_str), Some(encryption_key_str)) = (
        read_string(&mut env, &record_json),
        read_string(&mut env, &encryption_key),
    ) else {
        return std::ptr::null_mut();
    };
    let record: SyncRecord = match serde_json::from_str(&record_str) {
        Ok(r) => r,
        Err(_) => return std::ptr::null_mut(),
    };
    let encryption_key = match crypto::decode_encryption_key(&encryption_key_str) {
        Ok(key) => key,
        Err(_) => return std::ptr::null_mut(),
    };
    let plaintext = match crypto::decrypt_sync_record(&record, &encryption_key) {
        Ok(p) => p,
        Err(_) => return std::ptr::null_mut(),
    };
    let plaintext_str = match String::from_utf8(plaintext) {
        Ok(value) => value,
        Err(_) => return std::ptr::null_mut(),
    };
    match env.new_string(plaintext_str) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_verifySyncRecord<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    record_json: JString<'local>,
    device_public_key: JString<'local>,
) -> jboolean {
    let (Some(record_str), Some(public_key_str)) = (
        read_string(&mut env, &record_json),
        read_string(&mut env, &device_public_key),
    ) else {
        return 0;
    };
    u8::from(crypto::verify_sync_record_json(&record_str, &public_key_str).unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createDeviceRecord<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    chain_id: JString<'local>,
    device_id: JString<'local>,
    device_name: JString<'local>,
    encryption_key: JString<'local>,
    signing_key: JString<'local>,
) -> jstring {
    let (
        Some(chain_id_str),
        Some(device_id_str),
        Some(device_name_str),
        Some(encryption_key_str),
        Some(signing_key_str),
    ) = (
        read_string(&mut env, &chain_id),
        read_string(&mut env, &device_id),
        read_string(&mut env, &device_name),
        read_string(&mut env, &encryption_key),
        read_string(&mut env, &signing_key),
    )
    else {
        return std::ptr::null_mut();
    };
    let Ok(encryption_key) = crypto::decode_encryption_key(&encryption_key_str) else {
        return std::ptr::null_mut();
    };
    let Ok(signing_key) = crypto::decode_signing_key(&signing_key_str) else {
        return std::ptr::null_mut();
    };
    let Some(created_at_ms) = now_ms() else {
        return std::ptr::null_mut();
    };
    let Ok(record) = crypto::create_device_record_at(
        &chain_id_str,
        &device_id_str,
        device_name_str.as_bytes(),
        &encryption_key,
        &signing_key,
        created_at_ms,
    ) else {
        return std::ptr::null_mut();
    };
    let Ok(serialized) = serde_json::to_string(&record) else {
        return std::ptr::null_mut();
    };
    match env.new_string(serialized) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_verifyDeviceRecord<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    record_json: JString<'local>,
) -> jboolean {
    let Some(record_str) = read_string(&mut env, &record_json) else {
        return 0;
    };
    u8::from(crypto::verify_device_record_json(&record_str).unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_decryptDeviceRecord<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    encrypted_device_name: JString<'local>,
    device_id: JString<'local>,
    encryption_key: JString<'local>,
) -> jstring {
    let (Some(encrypted_name_str), Some(device_id_str), Some(encryption_key_str)) = (
        read_string(&mut env, &encrypted_device_name),
        read_string(&mut env, &device_id),
        read_string(&mut env, &encryption_key),
    ) else {
        return std::ptr::null_mut();
    };
    let encryption_key = match crypto::decode_encryption_key(&encryption_key_str) {
        Ok(key) => key,
        Err(_) => return std::ptr::null_mut(),
    };

    // Try decoding as full DeviceRecord if it contains JSON
    if encrypted_name_str.trim_start().starts_with('{') {
        if let Ok(record) = serde_json::from_str::<DeviceRecord>(&encrypted_name_str) {
            if let Ok(plaintext) = crypto::decrypt_device_record_v21(&record, &encryption_key) {
                if let Ok(s) = String::from_utf8(plaintext) {
                    if let Ok(output) = env.new_string(s) {
                        return output.into_raw();
                    }
                }
            }
        }
    }

    let plaintext =
        match crypto::decrypt_device_name(&encrypted_name_str, &device_id_str, &encryption_key) {
            Ok(p) => p,
            Err(_) => return std::ptr::null_mut(),
        };
    let plaintext_str = match String::from_utf8(plaintext) {
        Ok(s) => s,
        Err(_) => return std::ptr::null_mut(),
    };
    match env.new_string(plaintext_str) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createCapabilityToken<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    chain_id: JString<'local>,
    device_id: JString<'local>,
    method: JString<'local>,
    path: JString<'local>,
    query: JString<'local>,
    body_json: JString<'local>,
    signing_key: JString<'local>,
    ttl_ms: jlong,
) -> jstring {
    let (
        Some(chain_id_str),
        Some(device_id_str),
        Some(method_str),
        Some(path_str),
        Some(query_str),
        Some(body_str),
        Some(signing_key_str),
    ) = (
        read_string(&mut env, &chain_id),
        read_string(&mut env, &device_id),
        read_string(&mut env, &method),
        read_string(&mut env, &path),
        read_string(&mut env, &query),
        read_string(&mut env, &body_json),
        read_string(&mut env, &signing_key),
    )
    else {
        return std::ptr::null_mut();
    };

    let Ok(signing_key) = crypto::decode_signing_key(&signing_key_str) else {
        return std::ptr::null_mut();
    };

    let Ok(token_str) = crypto::create_capability_token(
        &chain_id_str,
        &device_id_str,
        &method_str,
        &path_str,
        &query_str,
        body_str.as_bytes(),
        &signing_key,
        ttl_ms,
    ) else {
        return std::ptr::null_mut();
    };

    match env.new_string(token_str) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
