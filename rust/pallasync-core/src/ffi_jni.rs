// ffi_jni.rs
// JNI bindings for PallaSync Protocol 3.0 Android

use crate::crypto;
use crate::models::{
    DeviceMetaPlaintext, DeviceRecord, EnrollmentCertificate, EpochKeyEnvelope, InnerRecord,
    SyncRecord,
};
use base64::{Engine as _, engine::general_purpose::URL_SAFE_NO_PAD};
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use x25519_dalek::{PublicKey as X25519PublicKey, StaticSecret};

fn read_string<'local>(env: &mut JNIEnv<'local>, value: &JString<'local>) -> Option<String> {
    if value.is_null() {
        return None;
    }
    env.get_string(value).ok().map(Into::into)
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
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_generateDeviceKeys<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let (signing_key, kex_bytes) = crypto::generate_device_keys();
    let signing_key_b64 = URL_SAFE_NO_PAD.encode(signing_key.to_bytes());
    let public_key_b64 = URL_SAFE_NO_PAD.encode(signing_key.verifying_key().as_bytes());

    let static_secret = StaticSecret::from(kex_bytes);
    let x_pub = X25519PublicKey::from(&static_secret);
    let kex_private_b64 = URL_SAFE_NO_PAD.encode(kex_bytes);
    let kex_public_b64 = URL_SAFE_NO_PAD.encode(x_pub.as_bytes());

    #[derive(serde::Serialize)]
    struct DeviceKeysOutput<'a> {
        signing_key: &'a str,
        public_key: &'a str,
        kex_private_key: &'a str,
        kex_public_key: &'a str,
    }

    let output = DeviceKeysOutput {
        signing_key: &signing_key_b64,
        public_key: &public_key_b64,
        kex_private_key: &kex_private_b64,
        kex_public_key: &kex_public_b64,
    };

    let Ok(json_str) = serde_json::to_string(&output) else {
        return std::ptr::null_mut();
    };
    match env.new_string(json_str) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_deriveRootKeys<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    seed_phrase: JString<'local>,
    passphrase: JString<'local>,
) -> jstring {
    let Some(seed_str) = read_string(&mut env, &seed_phrase) else {
        return std::ptr::null_mut();
    };
    let pass_str = read_string(&mut env, &passphrase).unwrap_or_default();

    let Ok(root_keys) = crypto::derive_root_keys(&seed_str, &pass_str) else {
        return std::ptr::null_mut();
    };

    let admin_key_b64 = URL_SAFE_NO_PAD.encode(root_keys.admin_key.to_bytes());
    let recovery_kek_b64 = URL_SAFE_NO_PAD.encode(root_keys.recovery_kek);

    #[derive(serde::Serialize)]
    struct RootKeysOutput<'a> {
        chain_id: &'a str,
        chain_salt: &'a str,
        admin_public_key: &'a str,
        admin_signing_key: &'a str,
        recovery_kek: &'a str,
    }

    let output = RootKeysOutput {
        chain_id: &root_keys.chain_id,
        chain_salt: &root_keys.chain_salt,
        admin_public_key: &root_keys.admin_public_key,
        admin_signing_key: &admin_key_b64,
        recovery_kek: &recovery_kek_b64,
    };

    let Ok(json_str) = serde_json::to_string(&output) else {
        return std::ptr::null_mut();
    };
    match env.new_string(json_str) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_deriveEpochKeys<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    epoch_secret_b64: JString<'local>,
    chain_id: JString<'local>,
    epoch: jlong,
) -> jstring {
    let (Some(secret_str), Some(chain_id_str)) = (
        read_string(&mut env, &epoch_secret_b64),
        read_string(&mut env, &chain_id),
    ) else {
        return std::ptr::null_mut();
    };

    let Ok(secret_bytes) = crypto::decode_key_32(&secret_str, "epoch secret") else {
        return std::ptr::null_mut();
    };
    let Ok(chain_id_digest) = crypto::decode_key_32(&chain_id_str, "chain_id") else {
        return std::ptr::null_mut();
    };

    let Ok(keys) = crypto::derive_epoch_keys(&chain_id_digest, epoch as u32, &secret_bytes) else {
        return std::ptr::null_mut();
    };

    #[derive(serde::Serialize)]
    struct EpochKeysOutput<'a> {
        record_key: String,
        device_meta_key: String,
        collection_tag_key: String,
        epoch_commitment: &'a str,
    }

    let output = EpochKeysOutput {
        record_key: URL_SAFE_NO_PAD.encode(keys.record_key),
        device_meta_key: URL_SAFE_NO_PAD.encode(keys.device_meta_key),
        collection_tag_key: URL_SAFE_NO_PAD.encode(keys.collection_tag_key),
        epoch_commitment: &keys.epoch_commitment_b64u,
    };

    let Ok(json_str) = serde_json::to_string(&output) else {
        return std::ptr::null_mut();
    };
    match env.new_string(json_str) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createSyncRecord<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    chain_id: JString<'local>,
    generation: jint,
    record_id: JString<'local>,
    device_id: JString<'local>,
    epoch: jint,
    collection_tag: JString<'local>,
    inner_record_json: JString<'local>,
    record_key_b64: JString<'local>,
    signing_key_b64: JString<'local>,
) -> jstring {
    let (
        Some(chain_id_str),
        Some(record_id_str),
        Some(device_id_str),
        Some(inner_record_str),
        Some(record_key_str),
        Some(signing_key_str),
    ) = (
        read_string(&mut env, &chain_id),
        read_string(&mut env, &record_id),
        read_string(&mut env, &device_id),
        read_string(&mut env, &inner_record_json),
        read_string(&mut env, &record_key_b64),
        read_string(&mut env, &signing_key_b64),
    )
    else {
        return std::ptr::null_mut();
    };

    let collection_tag_opt = read_string(&mut env, &collection_tag);

    let Ok(inner_record) = serde_json::from_str::<InnerRecord>(&inner_record_str) else {
        return std::ptr::null_mut();
    };
    let Ok(record_key) = crypto::decode_key_32(&record_key_str, "record key") else {
        return std::ptr::null_mut();
    };
    let Ok(signing_key) = crypto::decode_signing_key(&signing_key_str) else {
        return std::ptr::null_mut();
    };

    let Ok(record) = crypto::create_sync_record(
        &chain_id_str,
        generation as u32,
        &record_id_str,
        &device_id_str,
        epoch as u32,
        collection_tag_opt.as_deref(),
        &inner_record,
        &record_key,
        &signing_key,
        false,
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
    record_key_b64: JString<'local>,
) -> jstring {
    let (Some(record_str), Some(key_str)) = (
        read_string(&mut env, &record_json),
        read_string(&mut env, &record_key_b64),
    ) else {
        return std::ptr::null_mut();
    };

    let Ok(record) = serde_json::from_str::<SyncRecord>(&record_str) else {
        return std::ptr::null_mut();
    };
    let Ok(record_key) = crypto::decode_key_32(&key_str, "record key") else {
        return std::ptr::null_mut();
    };

    let Ok(inner) = crypto::decrypt_sync_record(&record, &record_key) else {
        return std::ptr::null_mut();
    };

    let Ok(output_str) = serde_json::to_string(&inner) else {
        return std::ptr::null_mut();
    };
    match env.new_string(output_str) {
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
    generation: jint,
    device_id: JString<'local>,
    device_signing_key_b64: JString<'local>,
    device_kex_public_key_b64: JString<'local>,
    device_name: JString<'local>,
    key_protection: JString<'local>,
    meta_epoch: jint,
    device_meta_key_b64: JString<'local>,
    enrollment_certificate_json: JString<'local>,
) -> jstring {
    let (
        Some(chain_id_str),
        Some(device_id_str),
        Some(signing_key_str),
        Some(kex_pub_str),
        Some(name_str),
        Some(protection_str),
        Some(meta_key_str),
        Some(enrollment_json),
    ) = (
        read_string(&mut env, &chain_id),
        read_string(&mut env, &device_id),
        read_string(&mut env, &device_signing_key_b64),
        read_string(&mut env, &device_kex_public_key_b64),
        read_string(&mut env, &device_name),
        read_string(&mut env, &key_protection),
        read_string(&mut env, &device_meta_key_b64),
        read_string(&mut env, &enrollment_certificate_json),
    )
    else {
        return std::ptr::null_mut();
    };

    let Ok(signing_key) = crypto::decode_signing_key(&signing_key_str) else {
        return std::ptr::null_mut();
    };
    let Ok(meta_key) = crypto::decode_key_32(&meta_key_str, "device meta key") else {
        return std::ptr::null_mut();
    };
    let Ok(enrollment) = serde_json::from_str::<EnrollmentCertificate>(&enrollment_json) else {
        return std::ptr::null_mut();
    };

    let device_meta = DeviceMetaPlaintext {
        name: name_str,
        key_protection: protection_str,
        ext: None,
    };

    let Ok(record) = crypto::create_device_record(
        &chain_id_str,
        generation as u32,
        &device_id_str,
        &signing_key,
        &kex_pub_str,
        &device_meta,
        meta_epoch as u32,
        &meta_key,
        enrollment,
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
    record_json: JString<'local>,
    device_meta_key_b64: JString<'local>,
) -> jstring {
    let (Some(record_str), Some(key_str)) = (
        read_string(&mut env, &record_json),
        read_string(&mut env, &device_meta_key_b64),
    ) else {
        return std::ptr::null_mut();
    };

    let Ok(record) = serde_json::from_str::<DeviceRecord>(&record_str) else {
        return std::ptr::null_mut();
    };
    let Ok(meta_key) = crypto::decode_key_32(&key_str, "device meta key") else {
        return std::ptr::null_mut();
    };

    let Ok(plaintext) = crypto::decrypt_device_meta(&record, &meta_key) else {
        return std::ptr::null_mut();
    };

    let Ok(output_str) = serde_json::to_string(&plaintext) else {
        return std::ptr::null_mut();
    };
    match env.new_string(output_str) {
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
    aud: JString<'local>,
    chain_id: JString<'local>,
    signer_kind: JString<'local>,
    device_id: JString<'local>,
    method: JString<'local>,
    path: JString<'local>,
    query: JString<'local>,
    body_json: JString<'local>,
    signing_key_b64: JString<'local>,
    ttl_ms: jlong,
) -> jstring {
    let (
        Some(aud_str),
        Some(chain_id_str),
        Some(signer_kind_str),
        Some(method_str),
        Some(path_str),
        Some(query_str),
        Some(body_str),
        Some(signing_key_str),
    ) = (
        read_string(&mut env, &aud),
        read_string(&mut env, &chain_id),
        read_string(&mut env, &signer_kind),
        read_string(&mut env, &method),
        read_string(&mut env, &path),
        read_string(&mut env, &query),
        read_string(&mut env, &body_json),
        read_string(&mut env, &signing_key_b64),
    )
    else {
        return std::ptr::null_mut();
    };

    let device_id_opt = read_string(&mut env, &device_id);

    let Ok(signing_key) = crypto::decode_signing_key(&signing_key_str) else {
        return std::ptr::null_mut();
    };

    let Ok(token_str) = crypto::create_capability_token(
        &aud_str,
        &chain_id_str,
        &signer_kind_str,
        device_id_opt.as_deref(),
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

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_unwrapDeviceEpochEnvelope<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    envelope_json: JString<'local>,
    device_kex_private_key_b64: JString<'local>,
    chain_id: JString<'local>,
) -> jstring {
    let (Some(envelope_str), Some(kex_str), Some(chain_id_str)) = (
        read_string(&mut env, &envelope_json),
        read_string(&mut env, &device_kex_private_key_b64),
        read_string(&mut env, &chain_id),
    ) else {
        return std::ptr::null_mut();
    };

    let Ok(envelope) = serde_json::from_str::<EpochKeyEnvelope>(&envelope_str) else {
        return std::ptr::null_mut();
    };
    let Ok(kex_private_key) = crypto::decode_key_32(&kex_str, "X25519 private key") else {
        return std::ptr::null_mut();
    };
    let Ok(chain_id_digest) = crypto::decode_key_32(&chain_id_str, "chain_id") else {
        return std::ptr::null_mut();
    };

    let Ok(secret) = crypto::unwrap_device_epoch_envelope(
        &envelope,
        &kex_private_key,
        &chain_id_digest,
    ) else {
        return std::ptr::null_mut();
    };

    let output = URL_SAFE_NO_PAD.encode(secret);
    match env.new_string(output) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_wrapDeviceEpochEnvelope<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    envelope_id: JString<'local>,
    chain_id: JString<'local>,
    generation: jint,
    epoch: jint,
    previous_epoch_hash: JString<'local>,
    epoch_commitment: JString<'local>,
    recipient_device_id: JString<'local>,
    recipient_kex_public_key_b64: JString<'local>,
    signer_kind: JString<'local>,
    signer_device_id: JString<'local>,
    signing_key_b64: JString<'local>,
    epoch_secret_b64: JString<'local>,
) -> jstring {
    let (
        Some(envelope_id_str),
        Some(chain_id_str),
        Some(commitment_str),
        Some(recipient_device_str),
        Some(recipient_kex_pub_str),
        Some(signer_kind_str),
        Some(signing_key_str),
        Some(secret_str),
    ) = (
        read_string(&mut env, &envelope_id),
        read_string(&mut env, &chain_id),
        read_string(&mut env, &epoch_commitment),
        read_string(&mut env, &recipient_device_id),
        read_string(&mut env, &recipient_kex_public_key_b64),
        read_string(&mut env, &signer_kind),
        read_string(&mut env, &signing_key_b64),
        read_string(&mut env, &epoch_secret_b64),
    )
    else {
        return std::ptr::null_mut();
    };

    let prev_hash_opt = read_string(&mut env, &previous_epoch_hash);
    let signer_device_opt = read_string(&mut env, &signer_device_id);

    let Ok(signing_key) = crypto::decode_signing_key(&signing_key_str) else {
        return std::ptr::null_mut();
    };
    let Ok(secret) = crypto::decode_key_32(&secret_str, "epoch secret") else {
        return std::ptr::null_mut();
    };

    let Ok(envelope) = crypto::wrap_device_epoch_envelope(
        &envelope_id_str,
        &chain_id_str,
        generation as u32,
        epoch as u32,
        prev_hash_opt.as_deref(),
        &commitment_str,
        &recipient_device_str,
        &recipient_kex_pub_str,
        &signer_kind_str,
        signer_device_opt.as_deref(),
        &signing_key,
        &secret,
    ) else {
        return std::ptr::null_mut();
    };

    let Ok(serialized) = serde_json::to_string(&envelope) else {
        return std::ptr::null_mut();
    };
    match env.new_string(serialized) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_wrapRecoveryEpochEnvelope<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    envelope_id: JString<'local>,
    chain_id: JString<'local>,
    generation: jint,
    epoch: jint,
    previous_epoch_hash: JString<'local>,
    epoch_commitment: JString<'local>,
    signer_kind: JString<'local>,
    signer_device_id: JString<'local>,
    signing_key_b64: JString<'local>,
    recovery_kek_b64: JString<'local>,
    epoch_secret_b64: JString<'local>,
) -> jstring {
    let (
        Some(envelope_id_str),
        Some(chain_id_str),
        Some(commitment_str),
        Some(signer_kind_str),
        Some(signing_key_str),
        Some(kek_str),
        Some(secret_str),
    ) = (
        read_string(&mut env, &envelope_id),
        read_string(&mut env, &chain_id),
        read_string(&mut env, &epoch_commitment),
        read_string(&mut env, &signer_kind),
        read_string(&mut env, &signing_key_b64),
        read_string(&mut env, &recovery_kek_b64),
        read_string(&mut env, &epoch_secret_b64),
    )
    else {
        return std::ptr::null_mut();
    };

    let prev_hash_opt = read_string(&mut env, &previous_epoch_hash);
    let signer_device_opt = read_string(&mut env, &signer_device_id);

    let Ok(signing_key) = crypto::decode_signing_key(&signing_key_str) else {
        return std::ptr::null_mut();
    };
    let Ok(kek) = crypto::decode_key_32(&kek_str, "recovery kek") else {
        return std::ptr::null_mut();
    };
    let Ok(secret) = crypto::decode_key_32(&secret_str, "epoch secret") else {
        return std::ptr::null_mut();
    };

    let Ok(envelope) = crypto::wrap_recovery_epoch_envelope(
        &envelope_id_str,
        &chain_id_str,
        generation as u32,
        epoch as u32,
        prev_hash_opt.as_deref(),
        &commitment_str,
        &signer_kind_str,
        signer_device_opt.as_deref(),
        &signing_key,
        &kek,
        &secret,
        None,
    ) else {
        return std::ptr::null_mut();
    };

    let Ok(serialized) = serde_json::to_string(&envelope) else {
        return std::ptr::null_mut();
    };
    match env.new_string(serialized) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_unwrapRecoveryEpochEnvelope<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    envelope_json: JString<'local>,
    recovery_kek_b64: JString<'local>,
    chain_id: JString<'local>,
) -> jstring {
    let (Some(envelope_str), Some(kek_str), Some(chain_id_str)) = (
        read_string(&mut env, &envelope_json),
        read_string(&mut env, &recovery_kek_b64),
        read_string(&mut env, &chain_id),
    ) else {
        return std::ptr::null_mut();
    };

    let Ok(envelope) = serde_json::from_str::<EpochKeyEnvelope>(&envelope_str) else {
        return std::ptr::null_mut();
    };
    let Ok(kek) = crypto::decode_key_32(&kek_str, "recovery kek") else {
        return std::ptr::null_mut();
    };
    let Ok(chain_id_digest) = crypto::decode_key_32(&chain_id_str, "chain_id") else {
        return std::ptr::null_mut();
    };

    let Ok(secret) = crypto::unwrap_recovery_epoch_envelope(&envelope, &kek, &chain_id_digest) else {
        return std::ptr::null_mut();
    };

    let output = URL_SAFE_NO_PAD.encode(secret);
    match env.new_string(output) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createGenesisBundle<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    seed_phrase: JString<'local>,
    passphrase: JString<'local>,
    device_name: JString<'local>,
    key_protection: JString<'local>,
    aud: JString<'local>,
) -> jstring {
    let (
        Some(seed_str),
        Some(name_str),
        Some(prot_str),
        Some(aud_str),
    ) = (
        read_string(&mut env, &seed_phrase),
        read_string(&mut env, &device_name),
        read_string(&mut env, &key_protection),
        read_string(&mut env, &aud),
    ) else {
        return std::ptr::null_mut();
    };
    let pass_str = read_string(&mut env, &passphrase).unwrap_or_default();

    let Ok(bundle) = crypto::create_genesis_bundle(
        &seed_str,
        &pass_str,
        &name_str,
        &prot_str,
        &aud_str,
    ) else {
        return std::ptr::null_mut();
    };

    let Ok(serialized) = serde_json::to_string(&bundle) else {
        return std::ptr::null_mut();
    };
    match env.new_string(serialized) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createMnemonicEnrollmentBundle<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    seed_phrase: JString<'local>,
    passphrase: JString<'local>,
    device_name: JString<'local>,
    key_protection: JString<'local>,
    generation: jint,
    epoch: jint,
    recovery_envelope_json: JString<'local>,
    expected_parameters_hash: JString<'local>,
    expected_epoch_hash: JString<'local>,
    aud: JString<'local>,
) -> jstring {
    let (
        Some(seed_str),
        Some(name_str),
        Some(prot_str),
        Some(env_str),
        Some(params_hash_str),
        Some(epoch_hash_str),
        Some(aud_str),
    ) = (
        read_string(&mut env, &seed_phrase),
        read_string(&mut env, &device_name),
        read_string(&mut env, &key_protection),
        read_string(&mut env, &recovery_envelope_json),
        read_string(&mut env, &expected_parameters_hash),
        read_string(&mut env, &expected_epoch_hash),
        read_string(&mut env, &aud),
    ) else {
        return std::ptr::null_mut();
    };
    let pass_str = read_string(&mut env, &passphrase).unwrap_or_default();

    let Ok(bundle) = crypto::create_mnemonic_enrollment_bundle(
        &seed_str,
        &pass_str,
        &name_str,
        &prot_str,
        generation as u32,
        epoch as u32,
        &env_str,
        &params_hash_str,
        &epoch_hash_str,
        &aud_str,
    ) else {
        return std::ptr::null_mut();
    };

    let Ok(serialized) = serde_json::to_string(&bundle) else {
        return std::ptr::null_mut();
    };
    match env.new_string(serialized) {
        Ok(output) => output.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
