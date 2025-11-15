#![allow(non_snake_case)]

use crate::passkey::registration::{get_exclusion_list, register_passkey};
use jni::objects::{JClass, JObject, JString, JValueGen};
use jni::sys::{jobject, jobjectArray};
use jni::JNIEnv;
use tokio::runtime::Builder;

static KOTLIN_MODEL_CLASS: &str = "de/davis/keygo/rust/passkey/model/KeyGoRegistrationResponse";

#[unsafe(no_mangle)]
pub extern "system" fn Java_de_davis_keygo_rust_passkey_PasskeyManager_registerPasskey(
    mut env: JNIEnv,
    _cls: JClass,
    request_json: JString,
) -> jobject {
    let request_json: String = env.get_string(&request_json).expect("Couldn't get java string!").into();

    let rt = Builder::new_current_thread().build().unwrap();
    let response = rt.block_on(async move {
        register_passkey(&request_json).await
    });

    let response = match response {
        Ok(response) => response,
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Error occurred while registering passkey: {e}"));
            return JObject::null().into_raw();
        }
    };

    let j_response_json = env.new_string(response.response()).expect("Couldn't create java string!");
    let j_cred_id = env.byte_array_from_slice(response.credential_id()).expect("Couldn't create java byte array!");
    let j_bytes = env.byte_array_from_slice(response.private_key()).expect("Couldn't create java byte array!");

    let cls = env.find_class(KOTLIN_MODEL_CLASS).unwrap();
    let obj = env
        .new_object(
            cls,
            "(Ljava/lang/String;[B[B)V",
            &[
                JValueGen::Object(&j_response_json),
                JValueGen::Object(&j_cred_id),
                JValueGen::Object(&j_bytes)
            ],
        )
        .expect("new_object failed");

    obj.into_raw()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_de_davis_keygo_rust_passkey_PasskeyManager_getExcludedCredentials(
    mut env: JNIEnv,
    _cls: JClass,
    request_json: JString,
) -> jobjectArray {
    let request_json: String = env.get_string(&request_json).expect("Couldn't get java string!").into();

    let rt = Builder::new_current_thread().build().unwrap();
    let response = rt.block_on(async move {
        get_exclusion_list(&request_json).await
    });

    let response = match response {
        Ok(response) => response,
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Error occurred while getting excluded credential list: {e}"));
            return JObject::null().into_raw();
        }
    };

    let byte_array_class = env
        .find_class("[B")
        .expect("Failed to find byte[] class");

    let array = env.new_object_array(
        response.len() as i32,
        byte_array_class,
        JObject::null(),
    ).expect("Failed to crate outer array");

    for (i, bytes) in response.iter().enumerate() {
        let inner = env.byte_array_from_slice(bytes).expect("Failed to byte array");
        env.set_object_array_element(
            &array,
            i as i32,
            &inner,
        ).expect("Failed to set inner array");
    }

    array.into_raw()
}