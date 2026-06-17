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
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work)

    implementation(libs.koin.androidx.workmanager)

    implementation(projects.core.ui)
    implementation(projects.core.util)
    implementation(projects.core.item)
    implementation(projects.core.security)
}
