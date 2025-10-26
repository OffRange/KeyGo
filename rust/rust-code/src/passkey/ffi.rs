#![allow(non_snake_case)]

use crate::passkey::registration::register_passkey;
use jni::objects::{JClass, JObject, JString, JValueGen};
use jni::sys::jobject;
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
    let j_bytes = env.byte_array_from_slice(response.private_key()).expect("Couldn't create java byte array!");

    let cls = env.find_class(KOTLIN_MODEL_CLASS).unwrap();
    let obj = env
        .new_object(
            cls,
            "(Ljava/lang/String;[B)V",
            &[
                JValueGen::Object(&j_response_json),
                JValueGen::Object(&j_bytes)
            ],
        )
        .expect("new_object failed");

    obj.into_raw()
}