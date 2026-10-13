//! §§5.5/7.11: public recovery controls and expiring native session ownership, never private JSON.
use crate::{
    client_error::{ClientError, Result},
    local_transfer, recovery, recovery_flow,
    root_session::{self, Operation},
    strict::jcs,
};
use jni::{
    JNIEnv,
    objects::{JByteArray, JClass, JObject, JString, JValue},
    sys::{jboolean, jbyteArray, jlong, jobject, jstring},
};
use serde_json::Value;
use zeroize::Zeroizing;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_createRecoveryResult<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pair: jlong,
    result: JString<'local>,
    code: JString<'local>,
) -> jstring {
    let result = (|| {
        let code = if code.is_null() {
            None
        } else {
            Some(text(&mut env, &code)?)
        };
        local_transfer::encrypt(
            pair as u64,
            "result",
            &serde_json::json!({"result":text(&mut env, &result)?,"code":code}),
        )
    })();
    pair_output(&mut env, pair as u64, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_receiveRecoveryResult<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pair: jlong,
    packet: JString<'local>,
) -> jstring {
    let result = text(&mut env, &packet).and_then(|packet| {
        local_transfer::receive(pair as u64, packet.as_bytes(), |kind, payload| {
            if kind != "result" {
                return Err(ClientError("C_RECOVERY_SHARE_INVALID"));
            }
            Ok(local_transfer::Reply::Public(payload.clone()))
        })
    });
    pair_output(&mut env, pair as u64, result)
}

fn text(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Result<String> {
    env.get_string(value)
        .map(Into::into)
        .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))
}
fn value(env: &mut JNIEnv<'_>, input: &JString<'_>) -> Result<Value> {
    recovery::parse(text(env, input)?.as_bytes())
}
fn canonical(value: &Value) -> Result<String> {
    String::from_utf8(
        jcs::canonicalize_json(value).map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?,
    )
    .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateRecoveryPackage<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    package: JString<'local>,
    admin: JString<'local>,
    known_status: JString<'local>,
) -> jstring {
    let result = (|| {
        let package = value(&mut env, &package)?;
        let known = if known_status.is_null() {
            None
        } else {
            Some(text(&mut env, &known_status)?)
        };
        recovery::validate_package(&package, &text(&mut env, &admin)?, known.as_deref())?;
        canonical(&package)
    })();
    match output(&mut env, result) {
        Ok(value) => value,
        Err(error) => {
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_retryRecoveryTransfer(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    pair: jlong,
    sequence: jlong,
) -> jstring {
    let result = u64::try_from(sequence)
        .map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))
        .and_then(|sequence| local_transfer::retry_packet(pair as u64, sequence));
    pair_output(&mut env, pair as u64, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_touchRecoveryCollection(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    collector: jlong,
) {
    if let Err(error) = recovery_flow::touch_collection(collector as u64) {
        recovery_flow::end_collection(collector as u64);
        failure(&mut env, error)
    }
}
fn failure(env: &mut JNIEnv<'_>, error: ClientError) {
    let _ = env.exception_clear();
    let _ = env.throw_new("java/lang/IllegalStateException", error.0);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_validateRecoverySetState<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    manifest: JString<'local>,
    chain: JString<'local>,
    admin: JString<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let manifest = value(&mut env, &manifest)?;
        let statuses = statuses(&mut env, &chain)?;
        let pin = if pin.is_null() {
            None
        } else {
            Some(text(&mut env, &pin)?)
        };
        recovery::validate_manifest(&manifest, &text(&mut env, &admin)?)?;
        recovery::validate_status_chain(&statuses, &manifest, pin.as_deref())?;
        canonical(&serde_json::json!(statuses))
    })();
    match output(&mut env, result) {
        Ok(value) => value,
        Err(error) => {
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_cancelRecoverySetSessions<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    set_id: JString<'local>,
) {
    if let Err(error) =
        text(&mut env, &set_id).and_then(|set_id| recovery_flow::cancel_set(&set_id))
    {
        failure(&mut env, error);
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_advanceRecoveryStatus<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: jlong,
    operation: JString<'local>,
    manifest: JString<'local>,
    chain: JString<'local>,
    known_head: JString<'local>,
    state: JString<'local>,
    replacement: JString<'local>,
    now: jlong,
) -> jstring {
    let result = (|| {
        let replacement = if replacement.is_null() {
            None
        } else {
            Some(text(&mut env, &replacement)?)
        };
        let now = u64::try_from(now).map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        let operation = Operation::parse(&text(&mut env, &operation)?)?;
        canonical(&root_session::advance_recovery_status(
            root as u64,
            operation,
            &value(&mut env, &manifest)?,
            &statuses(&mut env, &chain)?,
            &text(&mut env, &known_head)?,
            &text(&mut env, &state)?,
            replacement.as_deref(),
            now,
        )?)
    })();
    match output(&mut env, result) {
        Ok(value) => value,
        Err(error) => {
            root_session::end(root as u64);
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}
fn output(env: &mut JNIEnv<'_>, result: Result<String>) -> Result<jstring> {
    result.and_then(|result| {
        env.new_string(result)
            .map(|value| value.into_raw())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })
}

fn statuses(env: &mut JNIEnv<'_>, input: &JString<'_>) -> Result<Vec<Value>> {
    value(env, input)?
        .as_array()
        .cloned()
        .ok_or(ClientError("C_RECOVERY_SHARE_INVALID"))
}

// The caller gives this JNI operation ownership of the mutable input. Wipe the JVM copy as well.
fn secret(env: &mut JNIEnv<'_>, input: &JByteArray<'_>) -> Result<Zeroizing<Vec<u8>>> {
    let length = env
        .get_array_length(input)
        .map_err(|_| ClientError("C_RECOVERY_WRAP_KEY_UNAVAILABLE"))?;
    let bytes = if (0..=recovery::MAX_PACKAGE as i32).contains(&length) {
        env.convert_byte_array(input)
            .map(Zeroizing::new)
            .map_err(|_| ClientError("C_RECOVERY_WRAP_KEY_UNAVAILABLE"))
    } else {
        Err(ClientError("C_RECOVERY_PARAMETER_INVALID"))
    };
    // Bounded chunks also erase an oversized input without a second unbounded allocation.
    let zeros = [0; 4096];
    for start in (0..length).step_by(zeros.len()) {
        let count = usize::try_from((length - start).min(zeros.len() as i32)).unwrap_or(0);
        if env
            .set_byte_array_region(input, start, &zeros[..count])
            .is_err()
        {
            return Err(ClientError("C_RECOVERY_WRAP_KEY_UNAVAILABLE"));
        }
    }
    bytes
}

fn key(env: &mut JNIEnv<'_>, input: &JByteArray<'_>) -> Result<Zeroizing<[u8; 32]>> {
    let bytes = secret(env, input)?;
    bytes
        .as_slice()
        .try_into()
        .map(Zeroizing::new)
        .map_err(|_| ClientError("C_RECOVERY_WRAP_KEY_UNAVAILABLE"))
}

fn pair_output(env: &mut JNIEnv<'_>, pair: u64, result: Result<Value>) -> jstring {
    let result = result.and_then(|value| canonical(&value));
    match output(env, result) {
        Ok(value) => value,
        Err(error) => {
            if error.0 != "C_MISSING_DEPENDENCY" {
                recovery_flow::end(pair);
            }
            failure(env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_prepareRecoverySet<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: jlong,
    operation: JString<'local>,
    generation: jlong,
    threshold: jlong,
    holders: JString<'local>,
    now: jlong,
) -> jstring {
    let result = (|| {
        let operation = Operation::parse(&text(&mut env, &operation)?)?;
        let generation =
            u32::try_from(generation).map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        let threshold =
            u16::try_from(threshold).map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        let holders = value(&mut env, &holders)?;
        let holders = holders
            .as_array()
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        let now = u64::try_from(now)
            .ok()
            .filter(|value| *value <= jcs::MAX_INTEGER)
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        canonical(&root_session::create_set(
            root as u64,
            operation,
            generation,
            threshold,
            holders,
            now,
        )?)
    })();
    match output(&mut env, result) {
        Ok(value) => value,
        Err(error) => {
            root_session::end(root as u64);
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_beginRecoveryTransfer<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    manifest: JString<'local>,
    admin: JString<'local>,
    ceremony: JString<'local>,
    session: JString<'local>,
    side: JString<'local>,
    role: JString<'local>,
    purpose: JString<'local>,
) -> jobject {
    let prepared = (|| {
        let manifest = value(&mut env, &manifest)?;
        local_transfer::begin(
            &manifest,
            &text(&mut env, &admin)?,
            &text(&mut env, &ceremony)?,
            &text(&mut env, &session)?,
            &text(&mut env, &side)?,
            &text(&mut env, &role)?,
            &text(&mut env, &purpose)?,
        )
    })();
    let (handle, descriptor) = match prepared {
        Ok(value) => value,
        Err(error) => {
            failure(&mut env, error);
            return std::ptr::null_mut();
        }
    };
    let allocated = (|| {
        let descriptor = env
            .new_string(canonical(&descriptor)?)
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        env.new_object(
            "com/yunfie/illustia/pallasync/PallaSyncRecoveryCandidate",
            "(JLjava/lang/String;)V",
            &[
                JValue::Long(handle as jlong),
                JValue::Object(&JObject::from(descriptor)),
            ],
        )
        .map(|object| object.into_raw())
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match allocated {
        Ok(value) => value,
        Err(error) => {
            recovery_flow::end(handle);
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_bindRecoveryTransfer<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    descriptor: JString<'local>,
) -> jstring {
    let result = value(&mut env, &descriptor)
        .and_then(|descriptor| local_transfer::bind_peer(handle as u64, &descriptor));
    match output(&mut env, result) {
        Ok(value) => value,
        Err(error) => {
            recovery_flow::end(handle as u64);
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_confirmRecoveryTransfer<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    sas: JString<'local>,
    confirmed: jboolean,
) {
    let result = text(&mut env, &sas)
        .and_then(|sas| local_transfer::confirm(handle as u64, &sas, confirmed != 0));
    if let Err(error) = result {
        recovery_flow::end(handle as u64);
        failure(&mut env, error)
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_endRecoveryTransfer(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    recovery_flow::end(handle as u64);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_distributeRecoveryShare<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: jlong,
    operation: JString<'local>,
    pair: jlong,
    index: jlong,
) -> jstring {
    let result = (|| {
        let operation = Operation::parse(&text(&mut env, &operation)?)?;
        let index = u8::try_from(index).map_err(|_| ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        recovery_flow::distribute(root as u64, operation, pair as u64, index)
    })();
    let pending = result
        .as_ref()
        .is_err_and(|error| error.0 == "C_MISSING_DEPENDENCY");
    let output = pair_output(&mut env, pair as u64, result);
    if output.is_null() && !pending {
        root_session::end(root as u64);
    }
    output
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_receiveRecoveryDealer<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: jlong,
    operation: JString<'local>,
    pair: jlong,
    packet: JString<'local>,
) -> jstring {
    let result = (|| {
        let operation = Operation::parse(&text(&mut env, &operation)?)?;
        let packet = text(&mut env, &packet)?;
        recovery_flow::receive_dealer(root as u64, operation, pair as u64, packet.as_bytes())
    })();
    let pending = result
        .as_ref()
        .is_err_and(|error| error.0 == "C_MISSING_DEPENDENCY");
    let output = pair_output(&mut env, pair as u64, result);
    if output.is_null() && !pending {
        root_session::end(root as u64);
    }
    output
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_receiveRecoveryHolder<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pair: jlong,
    packet: JString<'local>,
    instance: JString<'local>,
    device: JString<'local>,
    protection: JString<'local>,
    storage_ready: jboolean,
    replacement: jboolean,
    occupied: jboolean,
    now: jlong,
) -> jstring {
    let result = (|| {
        let device = if device.is_null() {
            None
        } else {
            Some(text(&mut env, &device)?)
        };
        let now = u64::try_from(now)
            .ok()
            .filter(|value| *value <= jcs::MAX_INTEGER)
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        recovery_flow::receive_holder(
            pair as u64,
            text(&mut env, &packet)?.as_bytes(),
            &text(&mut env, &instance)?,
            device.as_deref(),
            &text(&mut env, &protection)?,
            storage_ready != 0,
            replacement != 0,
            occupied != 0,
            now,
        )
    })();
    pair_output(&mut env, pair as u64, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_recoveryHolderPackage<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pair: jlong,
    status_chain: JString<'local>,
    credential: JByteArray<'local>,
) -> jstring {
    // Read and erase the credential before any public parsing can fail.
    let credential = if credential.is_null() {
        Ok(None)
    } else {
        secret(&mut env, &credential).map(Some)
    };
    let result = (|| {
        let credential = credential?;
        recovery_flow::holder_package(
            pair as u64,
            &statuses(&mut env, &status_chain)?,
            credential.as_ref().map(|bytes| bytes.as_slice()),
        )
    })();
    pair_output(&mut env, pair as u64, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_recoveryHolderWrapKey(
    mut env: JNIEnv<'_>,
    _class: JClass<'_>,
    pair: jlong,
) -> jbyteArray {
    let result = recovery_flow::holder_wrap_key(pair as u64).and_then(|key| {
        env.byte_array_from_slice(key.as_ref())
            .map(|bytes| bytes.into_raw())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    });
    match result {
        Ok(value) => value,
        Err(error) => {
            recovery_flow::end(pair as u64);
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_recoveryHolderPersisted<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pair: jlong,
    package: JString<'local>,
    readback_key: JByteArray<'local>,
) -> jstring {
    let readback = key(&mut env, &readback_key);
    let result =
        (|| recovery_flow::persisted(pair as u64, &value(&mut env, &package)?, &*readback?))();
    pair_output(&mut env, pair as u64, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_releaseRecoveryShare<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    pair: jlong,
    package: JString<'local>,
    wrap_key: JByteArray<'local>,
    authenticated: jboolean,
    consented: jboolean,
    compromised: jboolean,
    emergency_new_root: jboolean,
) -> jstring {
    let wrap_key = key(&mut env, &wrap_key);
    let result = (|| {
        recovery_flow::release_piece_guarded(
            pair as u64,
            &value(&mut env, &package)?,
            &*wrap_key?,
            authenticated != 0,
            consented != 0,
            compromised != 0,
            emergency_new_root != 0,
        )
    })();
    pair_output(&mut env, pair as u64, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_beginRecoveryCollection<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    manifest: JString<'local>,
    status_chain: JString<'local>,
    admin: JString<'local>,
    known_status: JString<'local>,
    ceremony: JString<'local>,
    operation: JString<'local>,
    setup_root: jlong,
    confirmed: jboolean,
    compromised: jboolean,
    emergency_new_root: jboolean,
) -> jlong {
    let result = (|| {
        let operation = Operation::parse(&text(&mut env, &operation)?)?;
        let mode = if setup_root != 0 {
            recovery_flow::CollectionMode::SetupTest {
                root: setup_root as u64,
                operation,
            }
        } else {
            recovery_flow::CollectionMode::Recover {
                operation,
                emergency_new_root: emergency_new_root != 0,
            }
        };
        let known_status = if known_status.is_null() {
            None
        } else {
            Some(text(&mut env, &known_status)?)
        };
        recovery_flow::begin_collection(
            &value(&mut env, &manifest)?,
            &statuses(&mut env, &status_chain)?,
            &text(&mut env, &admin)?,
            known_status.as_deref(),
            &text(&mut env, &ceremony)?,
            mode,
            confirmed != 0,
            compromised != 0,
        )
    })();
    match result {
        Ok(handle) => handle as jlong,
        Err(error) => {
            if setup_root != 0 {
                root_session::end(setup_root as u64);
            }
            failure(&mut env, error);
            0
        }
    }
}

fn collection_output(env: &mut JNIEnv<'_>, collector: u64, result: Result<Value>) -> jstring {
    match output(env, result.and_then(|value| canonical(&value))) {
        Ok(value) => value,
        Err(error) => {
            if error.0 != "C_MISSING_DEPENDENCY" {
                recovery_flow::end_collection(collector);
            }
            failure(env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_receiveRecoveryCollector<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    collector: jlong,
    pair: jlong,
    packet: JString<'local>,
) -> jstring {
    let result = text(&mut env, &packet).and_then(|packet| {
        recovery_flow::receive_collector(collector as u64, pair as u64, packet.as_bytes())
    });
    let pending = result
        .as_ref()
        .is_err_and(|error| error.0 == "C_MISSING_DEPENDENCY");
    let output = collection_output(&mut env, collector as u64, result);
    if output.is_null() && !pending {
        recovery_flow::end(pair as u64);
    }
    output
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_collectOwnRecoveryShare<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    collector: jlong,
    package: JString<'local>,
    wrap_key: JByteArray<'local>,
    instance: JString<'local>,
    authenticated: jboolean,
    consented: jboolean,
    compromised: jboolean,
) -> jstring {
    let wrap_key = key(&mut env, &wrap_key);
    let result = (|| {
        recovery_flow::collect_own_piece_guarded(
            collector as u64,
            &value(&mut env, &package)?,
            &*wrap_key?,
            &text(&mut env, &instance)?,
            authenticated != 0,
            consented != 0,
            compromised != 0,
        )
    })();
    collection_output(&mut env, collector as u64, result)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_finishRecoveryCollection<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    collector: jlong,
    confirmed: jboolean,
) -> jobject {
    let result = recovery_flow::finish_collection(collector as u64, confirmed != 0);
    let value = match result {
        Ok(value) => value,
        Err(error) => {
            recovery_flow::end_collection(collector as u64);
            failure(&mut env, error);
            return std::ptr::null_mut();
        }
    };
    let root = value["root_session"].as_u64().unwrap_or(0);
    let allocated = (|| {
        let identity = if root == 0 {
            JObject::null()
        } else {
            JObject::from(
                env.new_string(canonical(&value["identity"])?)
                    .map_err(|_| ClientError("C_LOCAL_LIMIT"))?,
            )
        };
        env.new_object(
            "com/yunfie/illustia/pallasync/PallaSyncRecoveryCompletion",
            "(JLjava/lang/String;Z)V",
            &[
                JValue::Long(root as jlong),
                JValue::Object(&identity),
                JValue::Bool(u8::from(value["threshold_test_passed"] == true)),
            ],
        )
        .map(|object| object.into_raw())
        .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match allocated {
        Ok(object) => object,
        Err(error) => {
            if root != 0 {
                root_session::end(root);
            }
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_endRecoveryCollection(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    collector: jlong,
) {
    recovery_flow::end_collection(collector as u64);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_activateRecoverySet<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: jlong,
    operation: JString<'local>,
    now: jlong,
) -> jstring {
    let result = (|| {
        let operation = Operation::parse(&text(&mut env, &operation)?)?;
        let now = u64::try_from(now)
            .ok()
            .filter(|value| *value <= jcs::MAX_INTEGER)
            .ok_or(ClientError("C_RECOVERY_PARAMETER_INVALID"))?;
        canonical(&root_session::activate_set(root as u64, operation, now)?)
    })();
    match output(&mut env, result) {
        Ok(value) => value,
        Err(error) => {
            root_session::end(root as u64);
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_unlockOfflineRecoveryKey<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    package: JString<'local>,
    credential: JByteArray<'local>,
) -> jbyteArray {
    let credential = secret(&mut env, &credential);
    let result = (|| {
        let package = value(&mut env, &package)?;
        recovery::validate_package(
            &package,
            crate::public_wire::s(&package["manifest"], "admin_public_key"),
            None,
        )?;
        let key = recovery::unwrap_offline_key(&package, &credential?)?;
        env.byte_array_from_slice(key.as_ref())
            .map(|bytes| bytes.into_raw())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}
