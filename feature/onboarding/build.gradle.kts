plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.onboarding"

    defaultConfig {
        missingDimensionStrategy("store", "playStore")
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.item)
    implementation(projects.core.identity)
    implementation(projects.feature.backup)
    implementation(projects.feature.autofill)

    testImplementation(libs.robolectric)
    testImplementation(testFixtures(projects.core.biometrics))
    testImplementation(testFixtures(projects.core.identity))
    testImplementation(testFixtures(projects.core.item))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.rust))
    testImplementation(testFixtures(projects.feature.autofill))
}
