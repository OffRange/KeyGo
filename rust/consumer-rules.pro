# Keep native method signatures so JNI linking works
-keepclasseswithmembernames class de.davis.keygo.rust.** {
    native <methods>;
}

-keepclassmembers class de.davis.keygo.rust.passkey.model.KeyGoRegistrationResponse {
    public <init>(java.lang.String, byte[]);
}
