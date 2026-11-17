plugins {
    alias(libs.plugins.keygo.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.davis.keygo.feature.account"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.util)
    implementation(projects.core.identity)
    implementation(projects.core.item)

    testImplementation(testFixtures(projects.core.biometrics))
    testImplementation(testFixtures(projects.core.identity))
    testImplementation(testFixtures(projects.core.security))
    testImplementation(testFixtures(projects.core.util))
    testImplementation(testFixtures(projects.core.rust))
}
