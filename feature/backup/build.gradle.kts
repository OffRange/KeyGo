plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.keygo.android.protobuf)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.backup"
}

dependencies {
    implementation(libs.androidx.navigation.compose)

    implementation(projects.core.util)
}
