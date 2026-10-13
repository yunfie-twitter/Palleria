//! Internal JNI for public peer controls and expiring native handles (§18.1).
use crate::{
    authority::Authority,
    client_error::{ClientError, Result},
    peer_enrollment as peer,
    public_wire::{fields, n, s},
    strict::jcs,
};
use jni::{
    JNIEnv,
    objects::{JByteArray, JClass, JObject, JString, JValue},
    sys::{jboolean, jlong, jobject, jstring},
};
use serde_json::Value;
use zeroize::Zeroizing;

fn text(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<String> {
    env.get_string(value)
        .map(Into::into)
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
}
fn json(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<Value> {
    jcs::parse(text(env, value)?.as_bytes()).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
}
fn authority(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<Authority> {
    let authority: Authority =
        serde_json::from_value(json(env, value)?).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
    authority.complete_cut()?;
    Ok(authority)
}
fn clock(now: jlong) -> Result<u64> {
    u64::try_from(now)
        .ok()
        .filter(|now| *now <= jcs::MAX_INTEGER)
        .ok_or(ClientError("C_SCHEMA_VIOLATION"))
}
fn canonical(value: &Value) -> Result<String> {
    String::from_utf8(jcs::canonicalize_json(value).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?)
        .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))
}
fn string_result(env: &mut JNIEnv<'_>, result: Result<String>, handle: Option<u64>) -> jstring {
    let result = result.and_then(|value| {
        env.new_string(value)
            .map(|value| value.into_raw())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    });
    match result {
        Ok(value) => value,
        Err(error) => {
            if let Some(handle) = handle {
                peer::end(handle);
            }
            let _ = env.exception_clear();
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}
fn unit_result(env: &mut JNIEnv<'_>, result: Result, handle: u64) {
    if let Err(error) = result {
        peer::end(handle);
        let _ = env.exception_clear();
        let _ = env.throw_new("java/lang/IllegalStateException", error.0);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validatePeerResponse<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    body: JByteArray<'local>,
    binding: JString<'local>,
    kind: JString<'local>,
    limit: jlong,
) -> jstring {
    let result = (|| {
        let raw = env
            .convert_byte_array(&body)
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let value = jcs::parse(&raw).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let binding = json(&mut env, &binding)?;
        let kind = text(&mut env, &kind)?;
        let value = match kind.as_str() {
            "offer" => crate::peer_response::offer_created(&value, &binding)?,
            "request" => crate::peer_response::request_created(&value, &binding)?,
            "reject" => crate::peer_response::rejected(&value, &binding)?,
            "requests" => {
                fields(
                    &binding,
                    &[("authority", "object"), ("offer", "object")],
                    &[],
                )?;
                let authority: Authority = serde_json::from_value(binding["authority"].clone())
                    .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
                authority.complete_cut()?;
                crate::peer_response::requests_page(
                    &value,
                    &authority,
                    &binding["offer"],
                    usize::try_from(limit).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
                )?
            }
            _ => return Err(ClientError("C_SCHEMA_VIOLATION")),
        };
        canonical(&value)
    })();
    string_result(&mut env, result, None)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validatePeerApproval<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    state: JString<'local>,
    body: JString<'local>,
    now: jlong,
) -> jstring {
    let result = (|| {
        let state = authority(&mut env, &state)?;
        let body = json(&mut env, &body)?;
        canonical(&peer::approval_candidate(
            handle as u64,
            &state,
            &body,
            clock(now)?,
        )?)
    })();
    string_result(&mut env, result, Some(handle as u64))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createPeerOffer<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    state: JString<'local>,
    device: JString<'local>,
    request: JString<'local>,
    now: jlong,
) -> jstring {
    let result = (|| {
        let state = authority(&mut env, &state)?;
        let device = text(&mut env, &device)?;
        let request = json(&mut env, &request)?;
        fields(
            &request,
            &[
                ("request_id", "uuid"),
                ("offer_nonce", "b32"),
                ("expires_at_ms", "int"),
            ],
            &[],
        )?;
        let nonce = crate::strict::verify::decode(s(&request, "offer_nonce"), Some(32))
            .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        canonical(&peer::offer(
            &state,
            &device,
            s(&request, "request_id"),
            &nonce,
            n(&request, "expires_at_ms"),
            clock(now)?,
        )?)
    })();
    string_result(&mut env, result, None)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_beginPeerSubject<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    offer: JString<'local>,
    state: JString<'local>,
    selected: jboolean,
    now: jlong,
) -> jobject {
    let mut allocated = None;
    let result = (|| {
        let state = authority(&mut env, &state)?;
        let offer = json(&mut env, &offer)?;
        let (handle, request) = peer::begin_subject(&offer, &state, selected != 0, clock(now)?)?;
        allocated = Some(handle);
        let public = env
            .new_string(canonical(&request)?)
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        env.new_object(
            "com/yunfie/illustia/pallasync/PallaSyncPeerCandidate",
            "(JLjava/lang/String;)V",
            &[
                JValue::Long(handle as jlong),
                JValue::Object(&JObject::from(public)),
            ],
        )
        .map(|value| value.into_raw())
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            if let Some(handle) = allocated {
                peer::end(handle);
            }
            let _ = env.exception_clear();
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_beginPeerApprover<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    offer: JString<'local>,
    state: JString<'local>,
    signing: JByteArray<'local>,
    kex: JByteArray<'local>,
    now: jlong,
) -> jlong {
    let result = (|| {
        let state = authority(&mut env, &state)?;
        let offer = json(&mut env, &offer)?;
        let signing = Zeroizing::new(
            env.convert_byte_array(&signing)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        let kex = Zeroizing::new(
            env.convert_byte_array(&kex)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        peer::begin_approver(&offer, &state, &signing, &kex, clock(now)?)
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
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_bindPeerRequest<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    request: JString<'local>,
    now: jlong,
) -> jstring {
    let result = (|| peer::bind_request(handle as u64, &json(&mut env, &request)?, clock(now)?))();
    string_result(&mut env, result, Some(handle as u64))
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_peerSas<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    now: jlong,
) -> jstring {
    let result = clock(now).and_then(|now| peer::visual_sas(handle as u64, now));
    string_result(&mut env, result, Some(handle as u64))
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_confirmPeerSas<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    observed: JString<'local>,
    confirmed: jboolean,
    now: jlong,
) {
    let result = (|| {
        peer::confirm(
            handle as u64,
            &text(&mut env, &observed)?,
            confirmed != 0,
            clock(now)?,
        )
    })();
    unit_result(&mut env, result, handle as u64)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_peerCertificate<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    now: jlong,
) -> jstring {
    let result = clock(now).and_then(|now| canonical(&peer::certificate(handle as u64, now)?));
    string_result(&mut env, result, Some(handle as u64))
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_acceptPeerCertificate<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    certificate: JString<'local>,
    now: jlong,
) {
    let result =
        (|| peer::accept_certificate(handle as u64, &json(&mut env, &certificate)?, clock(now)?))();
    unit_result(&mut env, result, handle as u64)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_endPeerSession<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
) {
    peer::end(handle as u64);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_preparePeerEnvelope<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    state: JString<'local>,
    epoch: jlong,
    secret: JByteArray<'local>,
    now: jlong,
) -> jstring {
    let result = (|| {
        let authority = authority(&mut env, &state)?;
        let epoch = u32::try_from(epoch).map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?;
        let secret = Zeroizing::new(
            env.convert_byte_array(&secret)
                .map_err(|_| ClientError("C_SCHEMA_VIOLATION"))?,
        );
        canonical(&peer::envelope(
            handle as u64,
            &authority,
            epoch,
            &secret,
            clock(now)?,
        )?)
    })();
    string_result(&mut env, result, Some(handle as u64))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_preparePeerSubject<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    state: JString<'local>,
    envelope: JString<'local>,
    metadata: JString<'local>,
    now: jlong,
) -> jobject {
    let mut allocated = Vec::new();
    let result = (|| {
        let authority = authority(&mut env, &state)?;
        let envelope = json(&mut env, &envelope)?;
        let metadata = json(&mut env, &metadata)?;
        fields(
            &metadata,
            &[
                ("name", "text"),
                (
                    "key_protection",
                    "os-keystore|hardware|webcrypto-nonextractable|passphrase-wrapped",
                ),
            ],
            &[],
        )?;
        let prepared = peer::prepare_subject(
            handle as u64,
            &authority,
            &envelope,
            s(&metadata, "name"),
            s(&metadata, "key_protection"),
            clock(now)?,
        )?;
        let arrays = env
            .new_object_array(6, "[B", JObject::null())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        for (index, key) in [
            &prepared.material.record,
            &prepared.material.signing,
            &prepared.material.kex,
            &prepared.material.metadata,
            &prepared.material.tag,
            &prepared.material.secret,
        ]
        .into_iter()
        .enumerate()
        {
            allocated.push(
                env.byte_array_from_slice(key)
                    .map_err(|_| ClientError("C_LOCAL_LIMIT"))?,
            );
            env.set_object_array_element(&arrays, index as i32, allocated.last().unwrap())
                .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        }
        let public = env
            .new_string(canonical(&prepared.public)?)
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        env.new_object(
            "com/yunfie/illustia/pallasync/PallaSyncPreparedKeys",
            "([[BLjava/lang/String;)V",
            &[
                JValue::Object(&JObject::from(arrays)),
                JValue::Object(&JObject::from(public)),
            ],
        )
        .map(|value| value.into_raw())
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            let _ = env.exception_clear();
            for array in allocated {
                let _ = env.set_byte_array_region(&array, 0, &[0; 32]);
            }
            peer::end(handle as u64);
            let _ = env.throw_new("java/lang/IllegalStateException", error.0);
            std::ptr::null_mut()
        }
    }
}
