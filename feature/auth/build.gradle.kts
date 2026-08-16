plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.auth"

    // AuthViewModel.onSessionEstablished logs a failed v1 import through android.util.Log, which is
    // the only record of why it failed. Unit tests cover that branch, and an unmocked android.jar
    // throws rather than returning, so the log call would fail the test instead of the assertion.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
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
