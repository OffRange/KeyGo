plugins {
    alias(libs.plugins.keygo.android.compose)
}

android {
    namespace = "de.davis.keygo.core.biometrics"

    testFixtures {
        enable = true
    }
}

dependencies {
    api(projects.core.security)
    implementation(libs.androidx.biometric)
}
