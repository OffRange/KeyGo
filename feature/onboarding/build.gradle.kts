plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.onboarding"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.item)
    implementation(projects.core.identity)
    implementation(projects.feature.backup)
    implementation(projects.feature.autofill)
    implementation(projects.migration.createAccess)

    implementation(libs.androidx.navigation.compose)
}
