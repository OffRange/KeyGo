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
    implementation(projects.legacyMigration)

    implementation(libs.androidx.navigation.compose)

    testImplementation(projects.rust)
    testImplementation(testFixtures(projects.core.identity))
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))
    testImplementation(testFixtures(projects.legacyMigration))
}
