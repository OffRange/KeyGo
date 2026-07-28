plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.auth"
}

dependencies {
    implementation(projects.core.identity)
    implementation(projects.core.item)
    implementation(projects.core.ui)
    implementation(projects.migration.createAccess)
    implementation(projects.migration.legacyData)

    implementation(libs.androidx.navigation.compose)
}
