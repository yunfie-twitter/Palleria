//! §7.11.4 local encrypted media; these outputs never enter relay or normal export paths.
use crate::{
    client_error::{ClientError, Result},
    recovery,
    strict::jcs,
};
use jni::{
    JNIEnv,
    objects::{JByteArray, JClass, JObject, JObjectArray, JString},
    sys::{jbyteArray, jint, jobjectArray, jstring},
};
use serde_json::Value;

const MAX_PORTABLE: usize = 5 + recovery::MAX_PACKAGE.div_ceil(3) * 4;
const MAX_FRAGMENTS: usize = 4096;
const FRAGMENT_OVERHEAD: usize = 256;
fn invalid() -> ClientError {
    ClientError("C_RECOVERY_SHARE_INVALID")
}
fn text(env: &mut JNIEnv<'_>, value: &JString<'_>, maximum: usize) -> Result<String> {
    let length = env
        .call_method(value, "length", "()I", &[])
        .and_then(|v| v.i())
        .map_err(|_| invalid())?;
    if length < 0 || length as usize > maximum {
        return Err(invalid());
    }
    let text: String = env.get_string(value).map_err(|_| invalid())?.into();
    if text.len() > maximum {
        return Err(invalid());
    }
    Ok(text)
}
fn checked_package(
    env: &mut JNIEnv<'_>,
    package: &JString<'_>,
    admin: &JString<'_>,
    pin: &JString<'_>,
) -> Result<Value> {
    let package = recovery::parse(text(env, package, recovery::MAX_PACKAGE)?.as_bytes())?;
    validate(env, &package, admin, pin)?;
    Ok(package)
}
fn validate(
    env: &mut JNIEnv<'_>,
    package: &Value,
    admin: &JString<'_>,
    pin: &JString<'_>,
) -> Result {
    let pin = if pin.is_null() {
        None
    } else {
        Some(text(env, pin, 43)?)
    };
    recovery::validate_package(package, &text(env, admin, 43)?, pin.as_deref())
}
fn failure(env: &mut JNIEnv<'_>, error: ClientError) {
    let _ = env.exception_clear();
    let _ = env.throw_new("java/lang/IllegalStateException", error.0);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_encodeRecoveryPortable<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    package: JString<'local>,
    admin: JString<'local>,
    pin: JString<'local>,
) -> jbyteArray {
    let result = (|| {
        let package = checked_package(&mut env, &package, &admin, &pin)?;
        let encoded = recovery::portable(&package)?;
        env.byte_array_from_slice(encoded.as_bytes())
            .map(|array| array.into_raw())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))
    })();
    match result {
        Ok(array) => array,
        Err(error) => {
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_decodeRecoveryPortable<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    encoded: JByteArray<'local>,
    admin: JString<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let length = env.get_array_length(&encoded).map_err(|_| invalid())?;
        if length < 0 || length as usize > MAX_PORTABLE {
            return Err(invalid());
        }
        let bytes = env.convert_byte_array(&encoded).map_err(|_| invalid())?;
        let package =
            recovery::parse_portable(std::str::from_utf8(&bytes).map_err(|_| invalid())?)?;
        validate(&mut env, &package, &admin, &pin)?;
        let canonical = String::from_utf8(recovery::canonical(&package)?).map_err(|_| invalid())?;
        env.new_string(canonical)
            .map(|s| s.into_raw())
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

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_recoveryPackageFragments<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    package: JString<'local>,
    admin: JString<'local>,
    pin: JString<'local>,
    part_bytes: jint,
) -> jobjectArray {
    let result = (|| {
        let package = checked_package(&mut env, &package, &admin, &pin)?;
        let part_bytes = usize::try_from(part_bytes).map_err(|_| invalid())?;
        let parts = recovery::fragments(&package, part_bytes)?;
        let array = env
            .new_object_array(parts.len() as jint, "java/lang/String", JObject::null())
            .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        for (index, part) in parts.iter().enumerate() {
            let value = String::from_utf8(recovery::canonical(part)?).map_err(|_| invalid())?;
            let value = env
                .new_string(value)
                .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
            env.set_object_array_element(&array, index as jint, &value)
                .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
            env.delete_local_ref(value)
                .map_err(|_| ClientError("C_LOCAL_LIMIT"))?;
        }
        Ok(array.into_raw())
    })();
    match result {
        Ok(array) => array,
        Err(error) => {
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_yunfie_illustia_pallasync_PallaSyncCore_assembleRecoveryFragments<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    fragments: JObjectArray<'local>,
    admin: JString<'local>,
    pin: JString<'local>,
) -> jstring {
    let result = (|| {
        let count = env.get_array_length(&fragments).map_err(|_| invalid())?;
        if !(1..=MAX_FRAGMENTS as jint).contains(&count) {
            return Err(invalid());
        }
        let mut total = 0usize;
        let mut assembly = recovery::FragmentAssembly::default();
        let mut package = None;
        for index in 0..count {
            let value = JString::from(
                env.get_object_array_element(&fragments, index)
                    .map_err(|_| invalid())?,
            );
            let json = text(&mut env, &value, MAX_PORTABLE + FRAGMENT_OVERHEAD)?;
            env.delete_local_ref(value).map_err(|_| invalid())?;
            total = total.checked_add(json.len()).ok_or_else(invalid)?;
            if total > MAX_PORTABLE + MAX_FRAGMENTS * FRAGMENT_OVERHEAD {
                return Err(invalid());
            }
            let fragment = jcs::parse(json.as_bytes()).map_err(|_| invalid())?;
            if let Some(complete) = assembly.accept(&fragment)? {
                package = Some(complete);
            }
        }
        if let Some(package) = package {
            validate(&mut env, &package, &admin, &pin)?;
            let value = String::from_utf8(recovery::canonical(&package)?).map_err(|_| invalid())?;
            env.new_string(value)
                .map(|s| s.into_raw())
                .map_err(|_| ClientError("C_LOCAL_LIMIT"))
        } else {
            Ok(std::ptr::null_mut())
        }
    })();
    match result {
        Ok(value) => value,
        Err(error) => {
            failure(&mut env, error);
            std::ptr::null_mut()
        }
    }
}
