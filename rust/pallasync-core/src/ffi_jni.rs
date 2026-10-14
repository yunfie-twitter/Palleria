// ffi_jni.rs
// JNI bindings for PallaSync Protocol 3.0 Android

use crate::crypto;
use crate::{
    client_error::ClientError,
    root_session::{self, Operation},
};
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JObject, JString, JValue};
use jni::sys::{jboolean, jbyteArray, jint, jlong, jobject, jstring};
use zeroize::Zeroizing;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateCurrentKeyRecipient<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    authority: JString<'local>,
    device: JString<'local>,
) -> jboolean {
    let result = (|| {
        let authority: crate::authority::Authority =
            serde_json::from_value(public_value(&mut env, &authority)?)
                .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let device = read_string(&mut env, &device).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        authority.current_key_recipient(&device)
    })();
    match result {
        Ok(()) => 1,
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateRevocationAcceptance<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    submitted: JString<'local>,
    pin: JString<'local>,
    device: JString<'local>,
) -> jstring {
    let result = (|| {
        let body = public_bytes(&mut env, &body)?;
        let submitted = public_value(&mut env, &submitted)?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let device = read_string(&mut env, &device).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        crate::bootstrap::revocation_acceptance(&body, &submitted, &pin, &device)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_deriveEpochMaterial<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    context: JString<'local>,
    secret: JByteArray<'local>,
) -> jobject {
    let material = (|| {
        let context = serde_json::from_value(public_value(&mut env, &context)?)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let secret = Zeroizing::new(
            env.convert_byte_array(&secret)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        crate::epoch_session::normal_material(context, &secret)
    })();
    let material = match material {
        Ok(value) => value,
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            return std::ptr::null_mut();
        }
    };
    let mut allocated = Vec::new();
    let result = (|| {
        let matrix = env
            .new_object_array(4, "[B", JObject::null())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        for (index, bytes) in [
            &material.record,
            &material.metadata,
            &material.tag,
            &material.secret,
        ]
        .iter()
        .enumerate()
        {
            allocated.push(
                env.byte_array_from_slice(*bytes)
                    .map_err(|_| ClientError("C_LOCAL_LIMIT"))?,
            );
            env.set_object_array_element(&matrix, index as jint, allocated.last().unwrap())
                .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        }
        env.new_object(
            "com/yunfie/illustia/pallasync/PallaSyncEpochMaterial",
            "([[B)V",
            &[JValue::Object(&JObject::from(matrix))],
        )
        .map(|v| v.into_raw())
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match result {
        Ok(value) => value,
        Err(e) => {
            let _ = env.exception_clear();
            for array in allocated {
                let _ = env.set_byte_array_region(&array, 0, &[0; 32]);
            }
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_openOwnEpochEnvelope<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    authority: JString<'local>,
    entry: JString<'local>,
    receipt: JString<'local>,
    device: JString<'local>,
    private: JByteArray<'local>,
) -> jbyteArray {
    let result = (|| {
        let authority = serde_json::from_value(public_value(&mut env, &authority)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let entry = public_value(&mut env, &entry)?;
        let receipt = public_value(&mut env, &receipt)?;
        let device = read_string(&mut env, &device).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let private = Zeroizing::new(
            env.convert_byte_array(&private)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        crate::epoch_session::open_own_envelope(&authority, &entry, &receipt, &device, &private)
    })();
    match result {
        Ok(secret) => env
            .byte_array_from_slice(secret.as_slice())
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateRetainedLogLink<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    entry: JString<'local>,
    receipt: JString<'local>,
    pin: JString<'local>,
    after: jlong,
    previous: JString<'local>,
) -> jstring {
    let result = (|| {
        let entry = public_value(&mut env, &entry)?;
        let receipt = public_value(&mut env, &receipt)?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let previous = read_string(&mut env, &previous).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let after = u64::try_from(after).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        crate::bootstrap::retained_log_link(&entry, &receipt, &pin, after, &previous)
            .map(serde_json::Value::String)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_recoverEpochSecret<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    operation: JString<'local>,
    authority: JString<'local>,
    body: JByteArray<'local>,
    epoch: jlong,
) -> jbyteArray {
    let result = (|| {
        let operation = Operation::parse(
            &read_string(&mut env, &operation).ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?,
        )?;
        let authority: crate::authority::Authority =
            serde_json::from_value(public_value(&mut env, &authority)?)
                .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let body = public_bytes(&mut env, &body)?;
        let envelope = crate::bootstrap::recovery_response(
            &body,
            &authority,
            epoch
                .try_into()
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        )?;
        let envelope =
            serde_json::from_value(envelope).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        root_session::with(handle as u64, operation, |session| {
            use base64::Engine;
            if session.roots.chain_id != authority.pin.chain_id
                || base64::engine::general_purpose::URL_SAFE_NO_PAD
                    .encode(session.roots.admin_key.verifying_key().as_bytes())
                    != authority.admin_public_key
            {
                return Err(ClientError("C_RECOVERY_ROOT_MISMATCH"));
            }
            let secret = crypto::unwrap_recovery_epoch_envelope(
                &envelope,
                &session.roots.recovery_kek,
                &session.roots.chain_id_digest,
            )
            .map_err(|e| {
                if e.starts_with("C_COMMITMENT_MISMATCH") {
                    ClientError("C_COMMITMENT_MISMATCH")
                } else {
                    ClientError("C_AEAD_FAILURE")
                }
            })?;
            Ok(Zeroizing::new(secret))
        })
    })();
    match result {
        Ok(secret) => env
            .byte_array_from_slice(secret.as_slice())
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_verifyDeviceKeyDelivery<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    envelope: JString<'local>,
    private: JByteArray<'local>,
) -> jboolean {
    let result = (|| {
        let value = public_value(&mut env, &envelope)?;
        let private = Zeroizing::new(
            env.convert_byte_array(&private)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        crate::epoch_session::verify_device_envelope(handle as u64, &value, &private)
    })();
    match result {
        Ok(()) => 1,
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateOwnEnvelopePage<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    expected: JString<'local>,
    authority: JString<'local>,
) -> jstring {
    let result = (|| {
        let body = public_bytes(&mut env, &body)?;
        let expected = public_value(&mut env, &expected)?;
        let authority = serde_json::from_value(public_value(&mut env, &authority)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::bootstrap::own_envelope_page(&body, &expected, &authority)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_beginEpochSession<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    context: JString<'local>,
    secret: JByteArray<'local>,
) -> jlong {
    let result = (|| {
        let context = serde_json::from_value(public_value(&mut env, &context)?)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let secret = Zeroizing::new(
            env.convert_byte_array(&secret)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        crate::epoch_session::begin(context, &secret)
    })();
    match result {
        Ok(handle) => handle as jlong,
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_endEpochSession<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    crate::epoch_session::end(handle as u64);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_epochCollectionTag<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    collection: JString<'local>,
) -> jstring {
    let result = (|| {
        let collection =
            read_string(&mut env, &collection).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        crate::epoch_session::tag(handle as u64, &collection)
    })();
    match result {
        Ok(Some(tag)) => env
            .new_string(tag)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Ok(None) => std::ptr::null_mut(),
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_decryptEpochRecord<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    record: JString<'local>,
) -> jstring {
    let result = (|| {
        let value = public_value(&mut env, &record)?;
        let inner = crate::epoch_session::decrypt_record(handle as u64, &value)?;
        serde_json::to_value(inner).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_decryptEpochSnapshotPart<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    record: JString<'local>,
    author_public_key: JString<'local>,
) -> jstring {
    let result = (|| {
        let value = public_value(&mut env, &record)?;
        let public_key =
            read_string(&mut env, &author_public_key).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        crate::epoch_session::decrypt_snapshot_part(handle as u64, &value, &public_key)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_decryptEpochDevice<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    record: JString<'local>,
) -> jstring {
    let result = (|| {
        let value = public_value(&mut env, &record)?;
        crate::epoch_session::decrypt_device(handle as u64, &value)
    })();
    public_result(&mut env, result)
}

fn public_bytes(
    env: &mut JNIEnv<'_>,
    bytes: &JByteArray<'_>,
) -> Result<serde_json::Value, ClientError> {
    let bytes = env
        .convert_byte_array(bytes)
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateInitialHistoryCut<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    authority: JString<'local>,
    body: JByteArray<'local>,
) -> jstring {
    let result = (|| {
        let authority = serde_json::from_value(public_value(&mut env, &authority)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::bootstrap::history_cut(&authority, &public_bytes(&mut env, &body)?)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateReceiptResponse<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    expected: JString<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let body = public_bytes(&mut env, &body)?;
        let expected = public_value(&mut env, &expected)?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::bootstrap::receipt_response(&body, &expected, &pin)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncSnapshotCore_validateSnapshotPartResponse<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    expected_record_id: JString<'local>,
    expected_head_hash: JString<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let response = public_bytes(&mut env, &body)?;
        let record_id =
            read_string(&mut env, &expected_record_id).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let head_hash =
            read_string(&mut env, &expected_head_hash).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::attestations::validate_snapshot_part_response(
            &response, &record_id, &head_hash, &pin,
        )
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateRecoveryEnvelopeResponse<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    authority: JString<'local>,
    epoch: jlong,
) -> jstring {
    let result = (|| {
        let body = public_bytes(&mut env, &body)?;
        let authority = serde_json::from_value(public_value(&mut env, &authority)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let epoch = epoch
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        crate::bootstrap::recovery_response(&body, &authority, epoch)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateEnrollmentAcceptance<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    submitted: JString<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let body = public_bytes(&mut env, &body)?;
        let submitted = public_value(&mut env, &submitted)?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::bootstrap::enrollment_acceptance(&body, &submitted, &pin)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateKeysAck<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    device: JString<'local>,
    epoch_hash: JString<'local>,
) -> jstring {
    let result = (|| {
        let body = public_bytes(&mut env, &body)?;
        let device = read_string(&mut env, &device).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let epoch_hash =
            read_string(&mut env, &epoch_hash).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        crate::bootstrap::keys_ack(&body, &device, &epoch_hash)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateEpochAcceptance<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    submitted: JString<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let body = public_bytes(&mut env, &body)?;
        let submitted = public_value(&mut env, &submitted)?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::bootstrap::epoch_acceptance(&body, &submitted, &pin)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_deviceCapabilityToken<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    binding: JString<'local>,
    body: JByteArray<'local>,
    now: jlong,
) -> jstring {
    let result = (|| {
        let binding = serde_json::from_value(public_value(&mut env, &binding)?)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let body = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        crate::device_session::token_at(handle as u64, binding, &body, now)
    })();
    match result {
        Ok(token) => env
            .new_string(token)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_beginAuthorityHistory<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    identity: JString<'local>,
    origin: JString<'local>,
    key: JString<'local>,
) -> jstring {
    let result = (|| {
        let body = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let body =
            crate::strict::jcs::parse(&body).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let identity = public_value(&mut env, &identity)?;
        let origin =
            read_string(&mut env, &origin).ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let key = read_string(&mut env, &key).ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let authority =
            crate::client_response::initial_parameters(&body, &identity, &origin, &key)?;
        serde_json::to_value(authority).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_advanceAuthorityHistory<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    authority: JString<'local>,
    entry: JString<'local>,
    receipt: JString<'local>,
) -> jstring {
    let result = (|| {
        let authority: crate::authority::Authority =
            serde_json::from_value(public_value(&mut env, &authority)?)
                .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let entry = public_value(&mut env, &entry)?;
        let receipt = public_value(&mut env, &receipt)?;
        serde_json::to_value(authority.advance(&entry, &receipt)?)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_bindAuthorityActionPayload<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    authority: JString<'local>,
    operation: JString<'local>,
    payload: JString<'local>,
) -> jstring {
    let result = (|| {
        let authority: crate::authority::Authority =
            serde_json::from_value(public_value(&mut env, &authority)?)
                .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let operation = public_value(&mut env, &operation)?;
        let payload = public_value(&mut env, &payload)?;
        serde_json::to_value(authority.with_action_payload(&operation, &payload)?)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateLogPage<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    pin: JString<'local>,
    generation: jlong,
    after: jlong,
    limit: jint,
) -> jstring {
    let result = (|| {
        let body = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let body =
            crate::strict::jcs::parse(&body).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::client_response::page(
            &body,
            "log",
            &pin,
            generation
                .try_into()
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            after
                .try_into()
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            limit
                .try_into()
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        )?;
        Ok(body)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_beginDeviceSession<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    signing: JByteArray<'local>,
    record: JByteArray<'local>,
) -> jlong {
    let result = (|| {
        let signing = Zeroizing::new(
            env.convert_byte_array(&signing)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        let record = Zeroizing::new(
            env.convert_byte_array(&record)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        crate::device_session::begin(&signing, &record)
    })();
    match result {
        Ok(handle) => handle as jlong,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_endDeviceSession<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    crate::device_session::end(handle as u64);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_requireNormalDataBoundary<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    input: JByteArray<'local>,
) {
    let result = (|| {
        let bytes = Zeroizing::new(
            env.convert_byte_array(&input)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        let value = crate::recovery::SecretJson(
            crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        crate::normal_data_boundary::check(&value.0)
    })();
    if let Err(error) = result {
        let _ = env.throw_new("java/lang/IllegalStateException", error.0);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_prepareRecordFrame<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    inner: JByteArray<'local>,
) -> jbyteArray {
    let result = (|| {
        let inner = Zeroizing::new(
            env.convert_byte_array(&inner)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        let frame = Zeroizing::new(crate::device_session::prepare_frame(&inner)?);
        env.byte_array_from_slice(&frame)
            .map(|array| array.into_raw())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match result {
        Ok(bytes) => bytes,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createPreparedRecord<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    context: JString<'local>,
    frame: JByteArray<'local>,
    nonce: JByteArray<'local>,
) -> jstring {
    let result = (|| {
        let context = serde_json::from_value(public_value(&mut env, &context)?)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let frame = Zeroizing::new(
            env.convert_byte_array(&frame)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        let nonce = env
            .convert_byte_array(&nonce)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let record = crate::device_session::create(handle as u64, context, &frame, &nonce)?;
        serde_json::to_value(record).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_decryptOwnPreparedRecord<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    record: JString<'local>,
) -> jstring {
    let result = (|| {
        let record = public_value(&mut env, &record)?;
        let inner = crate::device_session::decrypt_own(handle as u64, &record)?;
        serde_json::to_value(inner).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_compareLogHeads<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    log: JString<'local>,
    head: JString<'local>,
    pin: JString<'local>,
    generation: jlong,
) -> jboolean {
    let result = (|| {
        let log = public_value(&mut env, &log)?;
        let head = public_value(&mut env, &head)?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let generation = generation
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        crate::attestations::validate_head(&head, &pin)?;
        crate::client_response::compare_log_heads(&log, &head, generation)
    })();
    match result {
        Ok(()) => 1,
        Err(ClientError("C_MISSING_DEPENDENCY")) => 0,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateLogHead<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
) -> jstring {
    let result = (|| {
        let bytes = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let value =
            crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        crate::client_response::log_head(&value)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateRecordAuthority<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    authority: JString<'local>,
    record: JString<'local>,
    sequence: jlong,
) -> jboolean {
    let result = (|| {
        let authority: crate::authority::Authority =
            serde_json::from_value(public_value(&mut env, &authority)?)
                .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let record = public_value(&mut env, &record)?;
        let sequence: u64 = sequence
            .try_into()
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        if sequence == 0 || sequence > crate::strict::jcs::MAX_INTEGER {
            return Err(ClientError("C_SCHEMA_VIOLATION"));
        }
        authority.record_eligible(&record, sequence)
    })();
    match result {
        Ok(()) => 1,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            0
        }
    }
}

fn read_string<'local>(env: &mut JNIEnv<'local>, value: &JString<'local>) -> Option<String> {
    if value.is_null() {
        return None;
    }
    env.get_string(value).ok().map(Into::into)
}

fn public_result(
    env: &mut JNIEnv<'_>,
    result: crate::client_error::Result<serde_json::Value>,
) -> jstring {
    match result {
        Ok(value) => env
            .new_string(value.to_string())
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}

fn public_value<'local>(
    env: &mut JNIEnv<'local>,
    value: &JString<'local>,
) -> crate::client_error::Result<serde_json::Value> {
    let text = read_string(env, value).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
    crate::strict::jcs::parse(text.as_bytes()).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateReceiptInclusion<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    receipt: JString<'local>,
    entry: JString<'local>,
    pin: JString<'local>,
    anchor: JString<'local>,
) -> jboolean {
    let result = (|| {
        let receipt = public_value(&mut env, &receipt)?;
        let entry = public_value(&mut env, &entry)?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let anchor: crate::attestations::TransparencyAnchor =
            serde_json::from_value(public_value(&mut env, &anchor)?)
                .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::attestations::validate_receipt(&receipt, &pin)?;
        if crate::public_wire::s(&receipt, "transparency_id") != anchor.transparency_id
            || crate::public_wire::n(&receipt, "transparency_index") > anchor.index
        {
            return Err(ClientError("C_ACCEPTANCE_UNPROVEN"));
        }
        if crate::public_wire::n(&receipt, "transparency_index") == anchor.index
            && crate::public_wire::s(&receipt, "transparency_hash") != anchor.hash
        {
            return Err(ClientError("C_TRANSPARENCY_INVALID"));
        }
        crate::attestations::verify_receipt_transparency(&receipt, &entry, &pin)
    })();
    match result {
        Ok(()) => 1,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_observeTransparencyHead<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let bytes = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let value =
            crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::client_response::observed_transparency_head(&value, &pin)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateTransparencyHead<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    pin: JString<'local>,
    checkpoint: JString<'local>,
) -> jstring {
    let result = (|| {
        let bytes = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let value =
            crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let pin: crate::attestations::RelayPin =
            serde_json::from_value(public_value(&mut env, &pin)?)
                .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let checkpoint = public_value(&mut env, &checkpoint)?;
        crate::public_wire::fields(
            &checkpoint,
            &[
                ("anchor", "object"),
                ("verified_seq", "int"),
                ("verified_hash", "b32"),
                ("parameters_hash", "b32"),
                ("epoch_hash", "b32"),
            ],
            &[],
        )
        .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let anchor = serde_json::from_value(checkpoint["anchor"].clone())
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::client_response::transparency_head(
            &value,
            &pin,
            &anchor,
            crate::public_wire::n(&checkpoint, "verified_seq"),
            crate::public_wire::s(&checkpoint, "verified_hash"),
            crate::public_wire::s(&checkpoint, "parameters_hash"),
            crate::public_wire::s(&checkpoint, "epoch_hash"),
        )
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateTransparencyPage<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    pin: JString<'local>,
    anchor: JString<'local>,
    target: JString<'local>,
    limit: jint,
) -> jstring {
    let result = (|| {
        let bytes = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let value =
            crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let pin = serde_json::from_value(public_value(&mut env, &pin)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let anchor = serde_json::from_value(public_value(&mut env, &anchor)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let target = public_value(&mut env, &target)?;
        let next = crate::client_response::transparency_page(
            &value,
            &pin,
            &anchor,
            &target,
            limit
                .try_into()
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        )?;
        Ok(serde_json::json!({"next_anchor":next,"response":value}))
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateProblem<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    status: jint,
) -> jstring {
    let result = (|| {
        let bytes = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let parsed =
            crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        crate::client_response::problem(
            &parsed,
            status
                .try_into()
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        )
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateRecordsPage<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    pin: JString<'local>,
    generation: jlong,
    after: jlong,
    limit: jint,
) -> jstring {
    let result = (|| {
        let bytes = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let parsed =
            crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let pin = read_string(&mut env, &pin).ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let pin =
            crate::models::parse(&pin).map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::client_response::page(
            &parsed,
            "records",
            &pin,
            generation
                .try_into()
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            after
                .try_into()
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            limit
                .try_into()
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        )?;
        Ok(parsed)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateGenesisAcceptance<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    genesis: JString<'local>,
    identity: JString<'local>,
    origin: JString<'local>,
    key: JString<'local>,
) -> jstring {
    let result = (|| {
        let response = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let response =
            crate::strict::jcs::parse(&response).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let genesis = read_string(&mut env, &genesis).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let genesis = crate::strict::jcs::parse(genesis.as_bytes())
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let identity =
            read_string(&mut env, &identity).ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let identity = crate::strict::jcs::parse(identity.as_bytes())
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let origin = read_string(&mut env, &origin).ok_or(ClientError("C_RELAY_KEY_MISMATCH"))?;
        let key = read_string(&mut env, &key).ok_or(ClientError("C_RELAY_KEY_MISMATCH"))?;
        let mut checkpoint = crate::attestations::verify_genesis_acceptance(
            &response, &genesis, &identity, &origin, &key,
        )?;
        let authority = crate::authority::Authority::from_genesis(
            &response, &genesis, &identity, &origin, &key,
        )?;
        checkpoint["authority"] =
            serde_json::to_value(authority).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        // Internal checkpoint result only: preserve the exact public input/receipts for local proof storage.
        checkpoint["genesis"] = genesis;
        checkpoint["receipts"] = response["upload_receipts"].clone();
        Ok::<_, ClientError>(checkpoint)
    })();
    match result {
        Ok(value) => env
            .new_string(value.to_string())
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateKnownDevice<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    authority: JString<'local>,
    device: JString<'local>,
) -> jstring {
    let result = (|| {
        let local =
            read_string(&mut env, &authority).ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let local: crate::authority::Authority =
            crate::models::parse(&local).map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let device = read_string(&mut env, &device).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let device = crate::strict::jcs::parse(device.as_bytes())
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        local.known_device(&device)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateRecordUpload<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    submitted: JString<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let bytes = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let response =
            crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let objects = read_string(&mut env, &submitted).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let objects = crate::strict::jcs::parse(objects.as_bytes())
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let pin = read_string(&mut env, &pin).ok_or(ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let pin: crate::attestations::RelayPin =
            crate::models::parse(&pin).map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        crate::attestations::verify_record_upload_response(
            &response,
            objects
                .as_array()
                .ok_or(ClientError("C_SCHEMA_VIOLATION"))?,
            &pin,
        )?;
        Ok::<_, ClientError>(response)
    })();
    match result {
        Ok(value) => env
            .new_string(value.to_string())
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateHealth<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    health: JByteArray<'local>,
    origin: JString<'local>,
) -> jstring {
    let result = (|| {
        let bytes = env
            .convert_byte_array(&health)
            .map_err(|_| ClientError("C_RELAY_POLICY_UNAVAILABLE"))?;
        let health = crate::strict::jcs::parse(&bytes)
            .map_err(|_| ClientError("C_RELAY_DISCLOSURE_INVALID"))?;
        let origin =
            read_string(&mut env, &origin).ok_or(ClientError("C_RELAY_DISCLOSURE_INVALID"))?;
        crate::disclosure::validate_health(&health, &origin)?;
        Ok::<_, ClientError>(health)
    })();
    match result {
        Ok(v) => env
            .new_string(v.to_string())
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validatePolicy<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    markdown: JByteArray<'local>,
    disclosure: JByteArray<'local>,
    health: JByteArray<'local>,
    origin: JString<'local>,
    key: JString<'local>,
    revision: jlong,
    known_hash: JString<'local>,
    now: jlong,
) -> jstring {
    let result = (|| {
        let md = env
            .convert_byte_array(&markdown)
            .map_err(|_| ClientError("C_RELAY_POLICY_UNAVAILABLE"))?;
        let doc = env
            .convert_byte_array(&disclosure)
            .map_err(|_| ClientError("C_RELAY_POLICY_UNAVAILABLE"))?;
        let h = env
            .convert_byte_array(&health)
            .map_err(|_| ClientError("C_RELAY_POLICY_UNAVAILABLE"))?;
        let h =
            crate::strict::jcs::parse(&h).map_err(|_| ClientError("C_RELAY_DISCLOSURE_INVALID"))?;
        let origin =
            read_string(&mut env, &origin).ok_or(ClientError("C_RELAY_DISCLOSURE_INVALID"))?;
        let key = read_string(&mut env, &key).ok_or(ClientError("C_RELAY_KEY_MISMATCH"))?;
        let known = read_string(&mut env, &known_hash).unwrap_or_default();
        if !(0..=u32::MAX as jlong).contains(&revision)
            || !(0..=crate::strict::jcs::MAX_INTEGER as jlong).contains(&now)
        {
            return Err(ClientError("C_RELAY_DISCLOSURE_INVALID"));
        }
        crate::disclosure::validate_pair(
            &md,
            &doc,
            &h,
            &origin,
            &key,
            revision as u32,
            &known,
            now as u64,
        )
    })();
    match result {
        Ok(v) => env
            .new_string(serde_json::to_string(&v).unwrap_or_default())
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_objectHash<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jstring {
    let Some(text) = read_string(&mut env, &value) else {
        return std::ptr::null_mut();
    };
    let Ok(value) = crate::strict::jcs::parse(text.as_bytes()) else {
        return std::ptr::null_mut();
    };
    env.new_string(crate::strict::verify::object_hash(&value))
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_canonicalJson<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    value: JString<'local>,
) -> jstring {
    let Some(text) = read_string(&mut env, &value) else {
        return std::ptr::null_mut();
    };
    let Ok(value) = crate::strict::jcs::parse(text.as_bytes()) else {
        return std::ptr::null_mut();
    };
    let Ok(bytes) = crate::strict::jcs::canonicalize_json(&value) else {
        return std::ptr::null_mut();
    };
    let Ok(canonical) = String::from_utf8(bytes) else {
        return std::ptr::null_mut();
    };
    env.new_string(canonical)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_beginSnapshotVerification<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    record_key: JByteArray<'local>,
    header: JString<'local>,
    compaction_id: JString<'local>,
    part_count: jint,
) -> jlong {
    let result = (|| {
        let key = Zeroizing::new(
            env.convert_byte_array(&record_key)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        let header = public_value(&mut env, &header)?;
        let id = read_string(&mut env, &compaction_id).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        crate::snapshot::begin_verification(
            &key,
            &header,
            &id,
            part_count
                .try_into()
                .map_err(|_| ClientError("C_SNAPSHOT_INVALID"))?,
        )
    })();
    match result {
        Ok(handle) => handle as jlong,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_verifySnapshotPart<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    part: JString<'local>,
) -> jboolean {
    let result = (|| {
        let part = public_value(&mut env, &part)?;
        crate::snapshot::verification_part(handle as u64, &part)
    })();
    match result {
        Ok(()) => 1,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_finishSnapshotVerification<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    commitment: JString<'local>,
) -> jboolean {
    let result = read_string(&mut env, &commitment)
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))
        .and_then(|value| crate::snapshot::finish_verification(handle as u64, &value));
    match result {
        Ok(()) => 1,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_abortSnapshotVerification<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    crate::snapshot::abort_verification(handle as u64);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateDeletionProof<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    pin: JString<'local>,
    authority: JString<'local>,
    request: JString<'local>,
) -> jstring {
    let result = (|| {
        let bytes = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let proof =
            crate::strict::jcs::parse(&bytes).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let pin_value = public_value(&mut env, &pin)?;
        let pin: crate::attestations::RelayPin =
            serde_json::from_value(pin_value).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let authority: crate::authority::Authority =
            serde_json::from_value(public_value(&mut env, &authority)?)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let request = public_value(&mut env, &request)?;
        crate::attestations::validate_deletion_proof(&proof, &pin, &authority, &request)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateDeletionEvidenceBundle<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    receipt: JString<'local>,
    bundle: JString<'local>,
    authority: JString<'local>,
) -> jstring {
    let result = (|| {
        let receipt = public_value(&mut env, &receipt)?;
        let bundle = public_value(&mut env, &bundle)?;
        let authority: crate::authority::Authority =
            serde_json::from_value(public_value(&mut env, &authority)?)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        crate::attestations::validate_deletion_evidence_bundle(&receipt, &bundle, &authority)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateDeletionReceiptRevision<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    previous: JString<'local>,
    next: JString<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let previous = public_value(&mut env, &previous)?;
        let next = public_value(&mut env, &next)?;
        let pin: crate::attestations::RelayPin =
            serde_json::from_value(public_value(&mut env, &pin)?)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        crate::attestations::validate_deletion_receipt_revision(&previous, &next, &pin)
    })();
    public_result(&mut env, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createDeletionWitness<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    receipt: JString<'local>,
    authority: JString<'local>,
    device_id: JString<'local>,
    signing_seed: JByteArray<'local>,
    created_at_ms: jlong,
) -> jstring {
    let mut seed = env.convert_byte_array(&signing_seed).unwrap_or_default();
    let result = (|| {
        let receipt = public_value(&mut env, &receipt)?;
        let authority: crate::authority::Authority =
            serde_json::from_value(public_value(&mut env, &authority)?)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let device_id =
            read_string(&mut env, &device_id).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        crate::attestations::create_deletion_witness(
            &receipt,
            &authority,
            &device_id,
            &mut seed,
            u64::try_from(created_at_ms).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        )
    })();
    use zeroize::Zeroize;
    seed.zeroize();
    public_result(&mut env, result)
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
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_beginRootSession<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    mnemonic: JByteArray<'local>,
    passphrase: JByteArray<'local>,
    operation: JString<'local>,
    expected_chain: JString<'local>,
    expected_admin: JString<'local>,
) -> jlong {
    let result = (|| {
        let phrase = Zeroizing::new(
            env.convert_byte_array(&mnemonic)
                .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?,
        );
        let pass = Zeroizing::new(
            env.convert_byte_array(&passphrase)
                .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?,
        );
        let op = Operation::parse(
            &read_string(&mut env, &operation).ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?,
        )?;
        let chain = read_string(&mut env, &expected_chain);
        let admin = read_string(&mut env, &expected_admin);
        root_session::begin(&phrase, &pass, op, chain.as_deref(), admin.as_deref())
    })();
    match result {
        Ok(handle) => handle as jlong,
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_rootIdentity<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) -> jstring {
    match root_session::identity(handle as u64) {
        Ok(v) => env
            .new_string(v.to_string())
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createDeleteRequest<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    generation: jint,
    deletion_id: JString<'local>,
    parameters_hash: JString<'local>,
    epoch_hash: JString<'local>,
    created_at_ms: jlong,
) -> jstring {
    let result = (|| {
        let deletion_id =
            read_string(&mut env, &deletion_id).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let parameters_hash =
            read_string(&mut env, &parameters_hash).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let epoch_hash =
            read_string(&mut env, &epoch_hash).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        if generation < 0 || created_at_ms < 0 {
            return Err(ClientError("C_SCHEMA_VIOLATION"));
        }
        crate::root_session::delete_request(
            handle as u64,
            generation as u32,
            &deletion_id,
            &parameters_hash,
            &epoch_hash,
            created_at_ms,
        )
    })();
    match result {
        Ok(value) => env
            .new_string(value)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_endRootSession<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    root_session::end(handle as u64);
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_endSecretSessions<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) {
    crate::secret_lifetime::invalidate();
    root_session::end_all();
    crate::recovery_flow::end_all();
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_setSecretForeground<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    foreground: jboolean,
) {
    crate::secret_lifetime::foreground(foreground != 0);
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_touchRootSession<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    if let Err(e) = root_session::touch(handle as u64) {
        let _ = env.throw_new("java/lang/IllegalStateException", e.0);
    }
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_rootCapabilityToken<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    operation: JString<'local>,
    aud: JString<'local>,
    method: JString<'local>,
    path: JString<'local>,
    query: JString<'local>,
    body: JByteArray<'local>,
    now: jlong,
) -> jstring {
    let result = (|| {
        let op = Operation::parse(
            &read_string(&mut env, &operation).ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?,
        )?;
        let origin = read_string(&mut env, &aud).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let method = read_string(&mut env, &method).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let path = read_string(&mut env, &path).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let query = read_string(&mut env, &query).ok_or(ClientError("C_SCHEMA_VIOLATION"))?;
        let bytes = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        root_session::capability_at(
            handle as u64,
            op,
            &origin,
            &method,
            &path,
            &query,
            &bytes,
            now,
        )
    })();
    match result {
        Ok(s) => env
            .new_string(s)
            .map(|s| s.into_raw())
            .unwrap_or(std::ptr::null_mut()),
        Err(e) => {
            let _ = env.throw_new("java/lang/IllegalStateException", e.0);
            std::ptr::null_mut()
        }
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
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createGenesisBundle<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    device_name: JString<'local>,
    key_protection: JString<'local>,
    aud: JString<'local>,
) -> jobject {
    let (Some(name_str), Some(prot_str), Some(aud_str)) = (
        read_string(&mut env, &device_name),
        read_string(&mut env, &key_protection),
        read_string(&mut env, &aud),
    ) else {
        return std::ptr::null_mut();
    };
    let Ok(bundle) = root_session::with(handle as u64, Operation::Genesis, |session| {
        crypto::create_genesis_bundle_from_roots(&session.roots, &name_str, &prot_str, &aud_str)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    }) else {
        return std::ptr::null_mut();
    };

    prepared_keys(
        &mut env,
        serde_json::json!({
            "chain_id":bundle.chain_id,"device_id":bundle.device_id,"device_public_key":bundle.device_public_key,
            "device_kex_public_key":bundle.device_kex_public_key,"epoch":0,"epoch_commitment":bundle.epoch_commitment,
            "request_body_json":bundle.genesis_request_body_json,"keys_ack_body_json":null
        }),
        [
            &bundle.record_key,
            &bundle.device_signing_key,
            &bundle.device_kex_private_key,
            &bundle.device_meta_key,
            &bundle.collection_tag_key,
            &bundle.epoch_secret,
        ],
    )
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createMnemonicEnrollmentBundle<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    device_name: JString<'local>,
    key_protection: JString<'local>,
    generation: jlong,
    epoch: jlong,
    recovery_envelope_json: JString<'local>,
    expected_parameters_hash: JString<'local>,
    expected_epoch_hash: JString<'local>,
    aud: JString<'local>,
) -> jobject {
    let (
        Some(name_str),
        Some(prot_str),
        Some(env_str),
        Some(params_hash_str),
        Some(epoch_hash_str),
        Some(aud_str),
    ) = (
        read_string(&mut env, &device_name),
        read_string(&mut env, &key_protection),
        read_string(&mut env, &recovery_envelope_json),
        read_string(&mut env, &expected_parameters_hash),
        read_string(&mut env, &expected_epoch_hash),
        read_string(&mut env, &aud),
    )
    else {
        return std::ptr::null_mut();
    };
    if u32::try_from(generation).is_err() || u32::try_from(epoch).is_err() {
        return std::ptr::null_mut();
    }
    let Ok(bundle) = root_session::with(handle as u64, Operation::Enrollment, |session| {
        crypto::create_mnemonic_enrollment_bundle_from_roots(
            &session.roots,
            &name_str,
            &prot_str,
            generation as u32,
            epoch as u32,
            &env_str,
            &params_hash_str,
            &epoch_hash_str,
            &aud_str,
        )
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
    }) else {
        return std::ptr::null_mut();
    };

    prepared_keys(
        &mut env,
        serde_json::json!({
            "chain_id":bundle.chain_id,"device_id":bundle.device_id,"device_public_key":bundle.device_public_key,
            "device_kex_public_key":bundle.device_kex_public_key,"epoch":bundle.epoch,"epoch_commitment":bundle.epoch_commitment,
            "request_body_json":bundle.enroll_request_body_json,"keys_ack_body_json":bundle.keys_ack_request_body_json
        }),
        [
            &bundle.record_key,
            &bundle.device_signing_key,
            &bundle.device_kex_private_key,
            &bundle.device_meta_key,
            &bundle.collection_tag_key,
            &bundle.epoch_secret,
        ],
    )
}

/// This JSON contains only public identifiers, signed ciphertext and acknowledgement bindings.
/// On a failed JNI allocation, wipe even Java arrays that were already allocated.
fn prepared_keys(env: &mut JNIEnv<'_>, public: serde_json::Value, secrets: [&str; 6]) -> jobject {
    let mut allocated = Vec::new();
    let result = (|| {
        let output = env
            .new_object_array(6, "[B", JObject::null())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        for (index, secret) in secrets.iter().enumerate() {
            let decoded = Zeroizing::new(
                crate::strict::verify::decode(secret, Some(32))
                    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            );
            let array = env
                .byte_array_from_slice(&decoded)
                .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
            allocated.push(array);
            env.set_object_array_element(&output, index as jint, allocated.last().unwrap())
                .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        }
        let text = env
            .new_string(
                serde_json::to_string(&public).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
            )
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        let output = JObject::from(output);
        let text = JObject::from(text);
        env.new_object(
            "com/yunfie/illustia/pallasync/PallaSyncPreparedKeys",
            "([[BLjava/lang/String;)V",
            &[JValue::Object(&output), JValue::Object(&text)],
        )
        .map(|object| object.into_raw())
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match result {
        Ok(object) => object,
        Err(error) => {
            // An allocation may have left a pending exception; clear it only to wipe owned buffers,
            // then replace it with a sanitized protocol error.
            let _ = env.exception_clear();
            for array in allocated {
                let _ = env.set_byte_array_region(&array, 0, &[0; 32]);
            }
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_prepareEpochBundle<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: jlong,
    operation: JString<'local>,
    authority: JString<'local>,
    request: JString<'local>,
    seed: JByteArray<'local>,
) -> jobject {
    let result = (|| {
        let operation = Operation::parse(
            &read_string(&mut env, &operation).ok_or(ClientError("C_RECOVERY_AUTH_REQUIRED"))?,
        )?;
        let authority = serde_json::from_value(public_value(&mut env, &authority)?)
            .map_err(|_| ClientError("C_BOOTSTRAP_TRUST_REQUIRED"))?;
        let request = serde_json::from_value(public_value(&mut env, &request)?)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let seed = Zeroizing::new(
            env.convert_byte_array(&seed)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        crate::rotation::prepare(root as u64, operation, &authority, request, &seed)
    })();
    let prepared = match result {
        Ok(prepared) => prepared,
        Err(error) => {
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            return std::ptr::null_mut();
        }
    };
    let mut allocated = Vec::new();
    let result = (|| {
        let matrix = env
            .new_object_array(4, "[B", JObject::null())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        for (index, bytes) in [
            &prepared.material.record,
            &prepared.material.metadata,
            &prepared.material.tag,
            &prepared.material.secret,
        ]
        .iter()
        .enumerate()
        {
            allocated.push(
                env.byte_array_from_slice(*bytes)
                    .map_err(|_| ClientError("C_LOCAL_LIMIT"))?,
            );
            env.set_object_array_element(&matrix, index as jint, allocated.last().unwrap())
                .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        }
        let text = env
            .new_string(prepared.public.to_string())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        env.new_object(
            "com/yunfie/illustia/pallasync/PallaSyncPreparedEpoch",
            "([[BLjava/lang/String;)V",
            &[
                JValue::Object(&JObject::from(matrix)),
                JValue::Object(&JObject::from(text)),
            ],
        )
        .map(|v| v.into_raw())
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            let _ = env.exception_clear();
            for array in allocated {
                let _ = env.set_byte_array_region(&array, 0, &[0; 32]);
            }
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}
