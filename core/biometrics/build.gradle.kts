plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.core.biometrics"
}

dependencies {
    api(projects.core.security)
    implementation(libs.androidx.biometric)
}
